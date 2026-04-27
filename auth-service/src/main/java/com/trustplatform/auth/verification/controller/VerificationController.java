package com.trustplatform.auth.verification.controller;

import com.trustplatform.auth.verification.dto.request.ReviewVerificationRequest;
import com.trustplatform.auth.verification.dto.response.ReviewVerificationResponse;
import com.trustplatform.auth.verification.dto.request.SubmitVerificationRequest;
import com.trustplatform.auth.verification.dto.response.VerificationDocumentLinkResponse;
import com.trustplatform.auth.verification.dto.response.VerificationRequestItem;
import com.trustplatform.auth.verification.dto.response.SubmitVerificationResponse;
import com.trustplatform.auth.verification.dto.response.VerificationStatusResponse;
import com.trustplatform.auth.verification.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/submit")
    public ResponseEntity<SubmitVerificationResponse> submit(
            Authentication authentication,
            @Valid @RequestBody SubmitVerificationRequest request
    ) {
        String email = authentication.getName();
        SubmitVerificationResponse response = verificationService.submit(email, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<VerificationStatusResponse> status(Authentication authentication) {
        String email = authentication.getName();
        VerificationStatusResponse response = verificationService.getStatus(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewVerificationResponse> review(
            Authentication authentication,
            @Valid @RequestBody ReviewVerificationRequest request
    ) {
        ReviewVerificationResponse response = verificationService.review(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VerificationRequestItem>> listRequests(
            @RequestParam(required = false) String status
    ) {
        List<VerificationRequestItem> requests = verificationService.listRequests(status);
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/requests/{id}/document-link")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationDocumentLinkResponse> documentLink(
            Authentication authentication,
            @PathVariable String id
    ) {
        VerificationDocumentLinkResponse response = verificationService.generateDocumentLink(
                id,
                authentication.getName()
        );
        return ResponseEntity.ok(response);
    }
}
