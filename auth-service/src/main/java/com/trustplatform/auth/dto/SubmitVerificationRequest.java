package com.trustplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitVerificationRequest {
    @NotBlank(message = "documentUrl is required")
    private String documentUrl;
}
