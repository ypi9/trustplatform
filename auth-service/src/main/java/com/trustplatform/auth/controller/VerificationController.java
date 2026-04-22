package com.trustplatform.auth.controller;

import com.trustplatform.auth.dto.ReviewVerificationRequest;
import com.trustplatform.auth.dto.ReviewVerificationResponse;
import com.trustplatform.auth.dto.SubmitVerificationRequest;
import com.trustplatform.auth.dto.VerificationDocumentLinkResponse;
import com.trustplatform.auth.dto.VerificationRequestItem;
import com.trustplatform.auth.dto.SubmitVerificationResponse;
import com.trustplatform.auth.dto.VerificationStatusResponse;
import com.trustplatform.auth.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ReviewVerificationResponse> review(
            @Valid @RequestBody ReviewVerificationRequest request
    ) {
        ReviewVerificationResponse response = verificationService.review(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<List<VerificationRequestItem>> listRequests(
            @RequestParam(required = false) String status
    ) {
        List<VerificationRequestItem> requests = verificationService.listRequests(status);
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/requests/{id}/document-link")
    public ResponseEntity<VerificationDocumentLinkResponse> documentLink(@PathVariable String id) {
        VerificationDocumentLinkResponse response = verificationService.generateDocumentLink(id);
        return ResponseEntity.ok(response);
    }
}
