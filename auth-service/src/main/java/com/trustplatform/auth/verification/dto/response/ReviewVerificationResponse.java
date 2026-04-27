package com.trustplatform.auth.verification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReviewVerificationResponse {
    private String requestId;
    private String status;
    private String userVerificationLevel;
    private String reviewNotes;
    private String reviewedAt;
}
