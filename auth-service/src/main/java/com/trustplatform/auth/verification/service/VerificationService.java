package com.trustplatform.auth.verification.service;

import com.trustplatform.auth.audit.service.AuditLogService;
import com.trustplatform.auth.common.metrics.AppMetricsService;
import com.trustplatform.auth.storage.FileService;
import com.trustplatform.auth.user.service.UserLookupClient;
import com.trustplatform.auth.user.service.UserVerificationClient;
import com.trustplatform.auth.verification.dto.request.ReviewVerificationRequest;
import com.trustplatform.auth.verification.dto.response.ReviewVerificationResponse;
import com.trustplatform.auth.verification.dto.request.SubmitVerificationRequest;
import com.trustplatform.auth.verification.dto.response.VerificationDocumentLinkResponse;
import com.trustplatform.auth.verification.dto.response.VerificationRequestItem;
import com.trustplatform.auth.verification.dto.response.SubmitVerificationResponse;
import com.trustplatform.auth.verification.dto.response.VerificationStatusResponse;
import com.trustplatform.auth.verification.entity.VerificationRequest;
import com.trustplatform.auth.verification.entity.VerificationStatus;
import com.trustplatform.auth.verification.repository.VerificationRequestRepository;
import com.trustplatform.auth.storage.dto.S3UploadResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private final UserLookupClient userLookupClient;
    private final UserVerificationClient userVerificationClient;
    private final VerificationRequestRepository verificationRequestRepository;
    private final AuditLogService auditLogService;
    private final FileService fileService;
    private final AppMetricsService appMetricsService;

    public VerificationService(UserLookupClient userLookupClient, UserVerificationClient userVerificationClient,
                               VerificationRequestRepository verificationRequestRepository,
                               AuditLogService auditLogService, FileService fileService,
                               AppMetricsService appMetricsService) {
        this.userLookupClient = userLookupClient;
        this.userVerificationClient = userVerificationClient;
        this.verificationRequestRepository = verificationRequestRepository;
        this.auditLogService = auditLogService;
        this.fileService = fileService;
        this.appMetricsService = appMetricsService;
    }

    // ──────────────────────────────────────────────
    // SUBMIT: User submits a new verification request
    // ──────────────────────────────────────────────
    @Transactional
    public SubmitVerificationResponse submit(String email, SubmitVerificationRequest request) {
        var user = userLookupClient.getUserByEmail(email);

        // Validate input
        String documentKey = request.getDocumentKey();
        if (documentKey == null || documentKey.isBlank()) {
            documentKey = request.getFileUrl();
        }

        if (documentKey == null || documentKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentKey is required");
        }

        UUID requestId = fileService.extractRequestId(documentKey);
        if (request.getRequestId() != null && !request.getRequestId().equals(requestId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "requestId does not match the uploaded verification document");
        }
        if (!fileService.isOwnedByUser(documentKey, user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only submit verification documents that you uploaded");
        }

        S3UploadResult document = fileService.getFileMetadata(documentKey);

        var profile = userVerificationClient.getRequiredProfile(user.id());

        // ── State machine guard: only NONE or REJECTED can submit ──
        switch (profile.verificationLevel()) {
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
        verificationRequest.setId(requestId);
        verificationRequest.setUserId(user.id());
        verificationRequest.setDocumentKey(document.getObjectKey());
        verificationRequest.setDocumentOriginalName(document.getOriginalFilename());
        verificationRequest.setDocumentContentType(document.getContentType());
        verificationRequest.setDocumentSize(document.getSize());
        verificationRequest.setDocumentUrl(document.getObjectKey());
        verificationRequest.setStatus(VerificationStatus.PENDING);
        verificationRequestRepository.save(verificationRequest);

        // Update profile level: NONE/REJECTED → PENDING
        userVerificationClient.updateVerificationStatus(user.id(), VerificationStatus.PENDING);

        // Audit log
        Map<String, Object> submitMetadata = new LinkedHashMap<>();
        submitMetadata.put("verificationRequestId", verificationRequest.getId());
        submitMetadata.put("documentKey", document.getObjectKey());
        submitMetadata.put("documentOriginalName", document.getOriginalFilename());
        submitMetadata.put("documentContentType", document.getContentType());
        submitMetadata.put("documentSize", document.getSize());
        auditLogService.log("verification_submitted", user.id(), submitMetadata);
        appMetricsService.incrementVerificationRequests();

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
        var user = userLookupClient.getUserByEmail(email);
        var profile = userVerificationClient.getRequiredProfile(user.id());

        VerificationStatusResponse.LatestRequest latestRequest = null;

        var latest = verificationRequestRepository.findTopByUserIdOrderByCreatedAtDesc(user.id());
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
                profile.verificationLevel().name(),
                latestRequest
        );
    }

    // ──────────────────────────────────────────────
    // REVIEW: Admin approves or rejects a request
    // ──────────────────────────────────────────────
    @Transactional
    public ReviewVerificationResponse review(ReviewVerificationRequest request, String reviewerEmail) {
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
        verificationRequest.setReviewedBy(reviewerEmail);
        verificationRequest.setReviewNotes(request.getReviewNotes());
        verificationRequestRepository.save(verificationRequest);

        var profile = userVerificationClient.updateVerificationStatus(verificationRequest.getUserId(), decision);

        // Audit log
        String action = decision == VerificationStatus.APPROVED
                ? "verification_approved"
                : "verification_rejected";
        var admin = userLookupClient.getUserByEmail(reviewerEmail);
        Map<String, Object> reviewMetadata = new LinkedHashMap<>();
        reviewMetadata.put("verificationRequestId", requestId);
        reviewMetadata.put("decision", decision.name());
        reviewMetadata.put("reviewedBy", reviewerEmail);
        reviewMetadata.put("reviewNotes", request.getReviewNotes() != null ? request.getReviewNotes() : "");
        reviewMetadata.put("subjectUserId", verificationRequest.getUserId());
        auditLogService.log(action, admin.id(), reviewMetadata);
        if (decision == VerificationStatus.APPROVED) {
            appMetricsService.incrementVerificationApprovals();
        } else {
            appMetricsService.incrementVerificationRejections();
        }

        return new ReviewVerificationResponse(
                requestId.toString(),
                decision.name(),
                profile.verificationLevel().name(),
                verificationRequest.getReviewNotes(),
                verificationRequest.getReviewedAt().toString()
        );
    }

    // ──────────────────────────────────────────────
    // LIST: Admin lists verification requests
    // ──────────────────────────────────────────────
    public Page<VerificationRequestItem> listRequests(String status, int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }

        var pageable = PageRequest.of(page, size);
        Page<VerificationRequest> requests;

        if (status != null && !status.isBlank()) {
            VerificationStatus filterStatus;
            try {
                filterStatus = VerificationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid status filter: " + status + ". Must be PENDING, APPROVED, or REJECTED");
            }
            requests = verificationRequestRepository.findByStatusOrderByCreatedAtDesc(filterStatus, pageable);
        } else {
            requests = verificationRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return requests.map(req -> new VerificationRequestItem(
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
        ));
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

        var admin = userLookupClient.getUserByEmail(adminEmail);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("verificationRequestId", request.getId());
        metadata.put("documentKey", documentKey);
        metadata.put("expiresInSeconds", DOCUMENT_LINK_TTL.toSeconds());
        auditLogService.log("document_link_generated", admin.id(), metadata);

        return new VerificationDocumentLinkResponse(request.getId().toString(), downloadUrl);
    }
}
