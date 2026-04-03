package com.trustplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerificationRequestItem {
    private String requestId;
    private String userId;
    private String status;
    private String documentUrl;
    private String createdAt;
    private String reviewedAt;
}
