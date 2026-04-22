package com.trustplatform.auth.service;

import com.trustplatform.auth.dto.ReviewVerificationRequest;
import com.trustplatform.auth.dto.ReviewVerificationResponse;
import com.trustplatform.auth.dto.SubmitVerificationRequest;
import com.trustplatform.auth.dto.VerificationDocumentLinkResponse;
import com.trustplatform.auth.dto.VerificationRequestItem;
import com.trustplatform.auth.dto.SubmitVerificationResponse;
import com.trustplatform.auth.dto.VerificationStatusResponse;
import com.trustplatform.auth.entity.UserProfile;
import com.trustplatform.auth.entity.VerificationLevel;
import com.trustplatform.auth.entity.VerificationRequest;
import com.trustplatform.auth.entity.VerificationStatus;
import com.trustplatform.auth.repository.UserProfileRepository;
import com.trustplatform.auth.repository.UserRepository;
import com.trustplatform.auth.repository.VerificationRequestRepository;
import com.trustplatform.auth.storage.dto.S3UploadResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Verification State Machine:
 *
 * User submits verification:
 *   NONE     → PENDING  ✅
 *   REJECTED → PENDING  ✅ (user can retry after rejection)
 *   PENDING  → submit   ❌ "User already has a pending verification request"
 *   VERIFIED → submit   ❌ "Verified users cannot submit a new verification request"
 *
 * Admin reviews request:
 *   PENDING  → APPROVED → profile becomes VERIFIED  ✅
 *   PENDING  → REJECTED → profile becomes REJECTED  ✅
 *   APPROVED → review   ❌ "Only pending verification requests can be reviewed"
 *   REJECTED → review   ❌ "Only pending verification requests can be reviewed"
 */
@Service
public class VerificationService {

    private static final Duration DOCUMENT_LINK_TTL = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final AuditLogService auditLogService;
    private final FileService fileService;

    public VerificationService(UserRepository userRepository, UserProfileRepository userProfileRepository,
                               VerificationRequestRepository verificationRequestRepository,
                               AuditLogService auditLogService, FileService fileService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.verificationRequestRepository = verificationRequestRepository;
        this.auditLogService = auditLogService;
        this.fileService = fileService;
    }

    // ──────────────────────────────────────────────
    // SUBMIT: User submits a new verification request
    // ──────────────────────────────────────────────
    @Transactional
    public SubmitVerificationResponse submit(String email, SubmitVerificationRequest request) {
        // Validate input
        String documentKey = request.getDocumentKey();
        if (documentKey == null || documentKey.isBlank()) {
            documentKey = request.getFileUrl();
        }

        if (documentKey == null || documentKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentKey is required");
        }

        S3UploadResult document = fileService.getFileMetadata(documentKey);

        // Load user
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Load UserProfile
        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        // ── State machine guard: only NONE or REJECTED can submit ──
        switch (profile.getVerificationLevel()) {
            case VERIFIED:
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Verified users cannot submit a new verification request");
            case PENDING:
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User already has a pending verification request");
            case NONE:
            case REJECTED:
                // Allowed — continue
                break;
        }

        // Create new VerificationRequest
        VerificationRequest verificationRequest = new VerificationRequest();
        verificationRequest.setUserId(user.getId());
        verificationRequest.setDocumentKey(document.getObjectKey());
        verificationRequest.setDocumentOriginalName(document.getOriginalFilename());
        verificationRequest.setDocumentContentType(document.getContentType());
        verificationRequest.setDocumentSize(document.getSize());
        verificationRequest.setDocumentUrl(document.getObjectKey());
        verificationRequest.setStatus(VerificationStatus.PENDING);
        verificationRequestRepository.save(verificationRequest);

        // Update profile level: NONE/REJECTED → PENDING
        profile.setVerificationLevel(VerificationLevel.PENDING);
        userProfileRepository.save(profile);

        // Audit log
        auditLogService.log("verification_submitted", user.getId(),
                "{\"requestId\":\"" + verificationRequest.getId()
                + "\",\"documentKey\":\"" + document.getObjectKey()
                + "\",\"documentOriginalName\":\"" + document.getOriginalFilename()
                + "\",\"documentContentType\":\"" + document.getContentType()
                + "\",\"documentSize\":" + document.getSize() + "}");

        return new SubmitVerificationResponse(
                verificationRequest.getId().toString(),
                verificationRequest.getStatus().name(),
                verificationRequest.getDocumentKey(),
                verificationRequest.getDocumentOriginalName(),
                verificationRequest.getDocumentContentType(),
                verificationRequest.getDocumentSize(),
                verificationRequest.getDocumentUrl()
        );
    }

    // ──────────────────────────────────────────────
    // STATUS: User checks their verification status
    // ──────────────────────────────────────────────
    public VerificationStatusResponse getStatus(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        VerificationStatusResponse.LatestRequest latestRequest = null;

        var latest = verificationRequestRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent()) {
            VerificationRequest req = latest.get();
            latestRequest = new VerificationStatusResponse.LatestRequest(
                    req.getId().toString(),
                    req.getStatus().name(),
                    req.getDocumentKey(),
                    req.getDocumentOriginalName(),
                    req.getDocumentContentType(),
                    req.getDocumentSize(),
                    req.getDocumentUrl(),
                    req.getCreatedAt().toString()
            );
        }

        return new VerificationStatusResponse(
                profile.getVerificationLevel().name(),
                latestRequest
        );
    }

    // ──────────────────────────────────────────────
    // REVIEW: Admin approves or rejects a request
    // ──────────────────────────────────────────────
    @Transactional
    public ReviewVerificationResponse review(ReviewVerificationRequest request) {
        // Validate requestId format
        UUID requestId;
        try {
            requestId = UUID.fromString(request.getRequestId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requestId format");
        }

        // Find verification request
        VerificationRequest verificationRequest = verificationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request not found"));

        // ── State machine guard: only PENDING can be reviewed ──
        if (verificationRequest.getStatus() != VerificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only pending verification requests can be reviewed. Current status: "
                    + verificationRequest.getStatus().name());
        }

        // Parse and validate decision
        VerificationStatus decision;
        try {
            decision = VerificationStatus.valueOf(request.getDecision().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid decision. Must be APPROVED or REJECTED");
        }

        if (decision == VerificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Decision must be APPROVED or REJECTED, not PENDING");
        }
        // Update verification request
        verificationRequest.setStatus(decision);
        verificationRequest.setReviewedAt(Instant.now());
        verificationRequest.setReviewedBy("admin");
        verificationRequest.setReviewNotes(request.getReviewNotes());
        verificationRequestRepository.save(verificationRequest);

        // Update user profile
        UserProfile profile = userProfileRepository.findById(verificationRequest.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        if (decision == VerificationStatus.APPROVED) {
            profile.setVerificationLevel(VerificationLevel.VERIFIED);
            profile.setVerified(true);
        } else {
            profile.setVerificationLevel(VerificationLevel.REJECTED);
            profile.setVerified(false);
        }
        userProfileRepository.save(profile);

        // Audit log
        String action = decision == VerificationStatus.APPROVED
                ? "verification_approved"
                : "verification_rejected";
        auditLogService.log(action, verificationRequest.getUserId(),
                "{\"requestId\":\"" + requestId
                + "\",\"decision\":\"" + decision.name()
                + "\",\"reviewNotes\":\"" + (request.getReviewNotes() != null ? request.getReviewNotes() : "")
                + "\"}");

        return new ReviewVerificationResponse(
                requestId.toString(),
                decision.name(),
                profile.getVerificationLevel().name(),
                verificationRequest.getReviewNotes(),
                verificationRequest.getReviewedAt().toString()
        );
    }

    // ──────────────────────────────────────────────
    // LIST: Admin lists verification requests
    // ──────────────────────────────────────────────
    public List<VerificationRequestItem> listRequests(String status) {
        List<VerificationRequest> requests;

        if (status != null && !status.isBlank()) {
            VerificationStatus filterStatus;
            try {
                filterStatus = VerificationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid status filter: " + status + ". Must be PENDING, APPROVED, or REJECTED");
            }
            requests = verificationRequestRepository.findByStatusOrderByCreatedAtDesc(filterStatus);
        } else {
            requests = verificationRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        return requests.stream().map(req -> new VerificationRequestItem(
                req.getId().toString(),
                req.getUserId().toString(),
                req.getStatus().name(),
                req.getDocumentKey(),
                req.getDocumentOriginalName(),
                req.getDocumentContentType(),
                req.getDocumentSize(),
                req.getDocumentUrl(),
                req.getCreatedAt().toString(),
                req.getReviewedAt() != null ? req.getReviewedAt().toString() : null
        )).toList();
    }

    public VerificationDocumentLinkResponse generateDocumentLink(String requestId, String adminEmail) {
        UUID id;
        try {
            id = UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requestId format");
        }

        VerificationRequest request = verificationRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request not found"));

        String documentKey = request.getDocumentKey();
        if (documentKey == null || documentKey.isBlank()) {
            documentKey = request.getDocumentUrl();
        }
        if (documentKey == null || documentKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Verification request has no document key");
        }

        String downloadUrl = fileService.generateDownloadUrl(documentKey, DOCUMENT_LINK_TTL).toString();

        var admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found"));
        auditLogService.log("document_link_generated", admin.getId(),
                "{\"requestId\":\"" + request.getId()
                + "\",\"documentKey\":\"" + documentKey
                + "\",\"expiresInSeconds\":" + DOCUMENT_LINK_TTL.toSeconds() + "}");

        return new VerificationDocumentLinkResponse(request.getId().toString(), downloadUrl);
    }
}
