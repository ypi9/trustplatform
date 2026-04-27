package com.trustplatform.auth.verification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerificationDocumentLinkResponse {
    private String requestId;
    private String downloadUrl;
}
