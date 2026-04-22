package com.trustplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmitVerificationResponse {
    private String requestId;
    private String status;
    private String documentKey;
    private String documentOriginalName;
    private String documentContentType;
    private Long documentSize;
    private String documentUrl;
}
