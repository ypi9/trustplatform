package com.trustplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitVerificationRequest {
    @NotBlank(message = "fileUrl is required")
    private String fileUrl;
}
