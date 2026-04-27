package com.trustplatform.auth.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class SubmitVerificationRequest {
    private String documentKey;
    private UUID requestId;

    /**
     * Legacy compatibility alias. New clients should send documentKey.
     */
    private String fileUrl;
}
