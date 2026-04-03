package com.trustplatform.auth.service;

import com.trustplatform.auth.dto.ReviewVerificationRequest;
import com.trustplatform.auth.dto.ReviewVerificationResponse;
import com.trustplatform.auth.dto.SubmitVerificationRequest;
import com.trustplatform.auth.dto.VerificationRequestItem;
import com.trustplatform.auth.dto.VerificationResponse;
import com.trustplatform.auth.dto.VerificationStatusResponse;
import com.trustplatform.auth.entity.UserProfile;
import com.trustplatform.auth.entity.VerificationLevel;
import com.trustplatform.auth.entity.VerificationRequest;
import com.trustplatform.auth.entity.VerificationStatus;
import com.trustplatform.auth.repository.UserProfileRepository;
import com.trustplatform.auth.repository.UserRepository;
import com.trustplatform.auth.repository.VerificationRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VerificationService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final AuditLogService auditLogService;

    public VerificationService(UserRepository userRepository, UserProfileRepository userProfileRepository,
                               VerificationRequestRepository verificationRequestRepository,
                               AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.verificationRequestRepository = verificationRequestRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public VerificationResponse submit(String email, SubmitVerificationRequest request) {
        // Reject invalid input
        if (request.getDocumentUrl() == null || request.getDocumentUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentUrl is required");
        }

        // Get current user
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Load UserProfile
        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        // Reject if already verified
        if (profile.getVerificationLevel() == VerificationLevel.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already verified");
        }

        // Reject if pending review
        if (profile.getVerificationLevel() == VerificationLevel.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Verification request already pending");
        }

        // Create new VerificationRequest
        VerificationRequest verificationRequest = new VerificationRequest();
        verificationRequest.setUserId(user.getId());
        verificationRequest.setDocumentUrl(request.getDocumentUrl());
        verificationRequest.setStatus(VerificationStatus.PENDING);
        verificationRequestRepository.save(verificationRequest);

        // Update UserProfile verification level
        profile.setVerificationLevel(VerificationLevel.PENDING);
        userProfileRepository.save(profile);

        // Audit log
        auditLogService.log("verification_submitted", user.getId(),
                "{\"requestId\":\"" + verificationRequest.getId() + "\",\"documentUrl\":\"" + request.getDocumentUrl() + "\"}");

        return new VerificationResponse(
                verificationRequest.getId().toString(),
                verificationRequest.getStatus().name()
        );
    }

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
                    req.getDocumentUrl(),
                    req.getCreatedAt().toString()
            );
        }

        return new VerificationStatusResponse(
                profile.getVerificationLevel().name(),
                latestRequest
        );
    }

    @Transactional
    public ReviewVerificationResponse review(ReviewVerificationRequest request) {
        UUID requestId;
        try {
            requestId = UUID.fromString(request.getRequestId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requestId format");
        }

        // Find verification request
        VerificationRequest verificationRequest = verificationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request not found"));

        // Guard: reject if not PENDING
        if (verificationRequest.getStatus() != VerificationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Request already reviewed with status: " + verificationRequest.getStatus().name());
        }

        // Parse decision
        VerificationStatus decision = VerificationStatus.valueOf(request.getDecision());

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
        String action = decision == VerificationStatus.APPROVED ? "verification_approved" : "verification_rejected";
        auditLogService.log(action, verificationRequest.getUserId(),
                "{\"requestId\":\"" + requestId + "\",\"reviewNotes\":\"" + (request.getReviewNotes() != null ? request.getReviewNotes() : "") + "\"}");

        return new ReviewVerificationResponse(
                requestId.toString(),
                decision.name(),
                profile.getVerificationLevel().name()
        );
    }

    public List<VerificationRequestItem> listRequests(String status) {
        List<VerificationRequest> requests;

        if (status != null && !status.isBlank()) {
            try {
                VerificationStatus filterStatus = VerificationStatus.valueOf(status.toUpperCase());
                requests = verificationRequestRepository.findByStatusOrderByCreatedAtDesc(filterStatus);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
        } else {
            requests = verificationRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        return requests.stream().map(req -> new VerificationRequestItem(
                req.getId().toString(),
                req.getUserId().toString(),
                req.getStatus().name(),
                req.getDocumentUrl(),
                req.getCreatedAt().toString(),
                req.getReviewedAt() != null ? req.getReviewedAt().toString() : null
        )).toList();
    }
}
