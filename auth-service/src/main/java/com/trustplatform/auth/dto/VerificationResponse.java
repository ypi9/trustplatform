package com.trustplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerificationResponse {
    private String requestId;
    private String status;
}
