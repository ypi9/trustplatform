package com.trustplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ReviewVerificationRequest {
    @NotBlank(message = "requestId is required")
    private String requestId;

    @NotNull(message = "decision is required")
    @Pattern(regexp = "APPROVED|REJECTED", message = "decision must be APPROVED or REJECTED")
    private String decision;

    private String reviewNotes;
}
