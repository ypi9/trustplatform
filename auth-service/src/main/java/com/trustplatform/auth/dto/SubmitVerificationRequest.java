package com.trustplatform.auth.dto;

import lombok.Data;

@Data
public class SubmitVerificationRequest {
    private String documentKey;

    /**
     * Legacy compatibility alias. New clients should send documentKey.
     */
    private String fileUrl;
}
