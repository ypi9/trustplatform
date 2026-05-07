package com.trustplatform.auth.user.service;

import com.trustplatform.auth.verification.entity.VerificationLevel;

import java.util.UUID;

public record UserProfileSnapshot(UUID userId, boolean verified, VerificationLevel verificationLevel) {
}
