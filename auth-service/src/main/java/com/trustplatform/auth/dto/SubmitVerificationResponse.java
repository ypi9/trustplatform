package com.trustplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmitVerificationResponse {
    private String requestId;
    private String status;
    private String documentUrl;
}