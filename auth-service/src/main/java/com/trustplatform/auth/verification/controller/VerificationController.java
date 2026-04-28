package com.trustplatform.auth.verification.controller;

import com.trustplatform.auth.verification.dto.request.ReviewVerificationRequest;
import com.trustplatform.auth.verification.dto.response.ReviewVerificationResponse;
import com.trustplatform.auth.verification.dto.request.SubmitVerificationRequest;
import com.trustplatform.auth.verification.dto.response.VerificationDocumentLinkResponse;
import com.trustplatform.auth.verification.dto.response.VerificationRequestItem;
import com.trustplatform.auth.verification.dto.response.SubmitVerificationResponse;
import com.trustplatform.auth.verification.dto.response.VerificationStatusResponse;
import com.trustplatform.auth.common.api.ApiSuccessResponse;
import com.trustplatform.auth.common.api.ApiSuccessResponseFactory;
import com.trustplatform.auth.verification.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/verification")
public class VerificationController {

    private final VerificationService verificationService;
    private final ApiSuccessResponseFactory successResponseFactory;

    public VerificationController(VerificationService verificationService,
                                  ApiSuccessResponseFactory successResponseFactory) {
        this.verificationService = verificationService;
        this.successResponseFactory = successResponseFactory;
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiSuccessResponse<SubmitVerificationResponse>> submit(
            Authentication authentication,
            @Valid @RequestBody SubmitVerificationRequest request
    ) {
        String email = authentication.getName();
        SubmitVerificationResponse response = verificationService.submit(email, request);
        return ResponseEntity.ok(successResponseFactory.build(response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiSuccessResponse<VerificationStatusResponse>> status(Authentication authentication) {
        String email = authentication.getName();
        VerificationStatusResponse response = verificationService.getStatus(email);
        return ResponseEntity.ok(successResponseFactory.build(response));
    }

    @PostMapping("/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiSuccessResponse<ReviewVerificationResponse>> review(
            Authentication authentication,
            @Valid @RequestBody ReviewVerificationRequest request
    ) {
        ReviewVerificationResponse response = verificationService.review(request, authentication.getName());
        return ResponseEntity.ok(successResponseFactory.build(response));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiSuccessResponse<java.util.List<VerificationRequestItem>>> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var requests = verificationService.listRequests(status, page, size);
        return ResponseEntity.ok(successResponseFactory.buildPage(requests));
    }

    @GetMapping("/requests/{id}/document-link")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiSuccessResponse<VerificationDocumentLinkResponse>> documentLink(
            Authentication authentication,
            @PathVariable String id
    ) {
        VerificationDocumentLinkResponse response = verificationService.generateDocumentLink(
                id,
                authentication.getName()
        );
        return ResponseEntity.ok(successResponseFactory.build(response));
    }
}
