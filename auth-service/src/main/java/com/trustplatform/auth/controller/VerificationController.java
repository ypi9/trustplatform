package com.trustplatform.auth.controller;

import com.trustplatform.auth.dto.SubmitVerificationRequest;
import com.trustplatform.auth.dto.VerificationResponse;
import com.trustplatform.auth.dto.VerificationStatusResponse;
import com.trustplatform.auth.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/submit")
    public ResponseEntity<VerificationResponse> submit(
            Authentication authentication,
            @Valid @RequestBody SubmitVerificationRequest request
    ) {
        String email = authentication.getName();
        VerificationResponse response = verificationService.submit(email, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<VerificationStatusResponse> status(Authentication authentication) {
        String email = authentication.getName();
        VerificationStatusResponse response = verificationService.getStatus(email);
        return ResponseEntity.ok(response);
    }
}
