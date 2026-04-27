package com.trustplatform.auth.verification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerificationRequestItem {
    private String requestId;
    private String userId;
    private String status;
    private String documentKey;
    private String documentOriginalName;
    private String documentContentType;
    private Long documentSize;
    private String documentUrl;
    private String createdAt;
    private String reviewedAt;
}
