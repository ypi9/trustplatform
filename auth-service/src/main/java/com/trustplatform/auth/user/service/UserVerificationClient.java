package com.trustplatform.auth.user.service;

import com.trustplatform.auth.verification.entity.VerificationStatus;

import java.util.UUID;

public interface UserVerificationClient {

    UserProfileSnapshot getRequiredProfile(UUID userId);

    UserProfileSnapshot updateVerificationStatus(UUID userId, VerificationStatus status);
}
