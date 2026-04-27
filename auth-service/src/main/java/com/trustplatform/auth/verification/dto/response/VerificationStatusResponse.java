package com.trustplatform.auth.verification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerificationStatusResponse {
    private String verificationLevel;
    private LatestRequest latestRequest;

    @Data
    @AllArgsConstructor
    public static class LatestRequest {
        private String requestId;
        private String status;
        private String documentKey;
        private String documentOriginalName;
        private String documentContentType;
        private Long documentSize;
        private String documentUrl;
        private String createdAt;
    }
}
