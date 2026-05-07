package com.trustplatform.auth.user.service;

import com.trustplatform.auth.user.entity.UserProfile;
import com.trustplatform.auth.user.repository.UserProfileRepository;
import com.trustplatform.auth.verification.entity.VerificationLevel;
import com.trustplatform.auth.verification.entity.VerificationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UserProfileService implements UserProfileClient, UserVerificationClient {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    @Transactional
    public void createEmptyProfile(UUID userId) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setFullName("");
        profile.setPhone("");
        profile.setVerified(false);
        profile.setVerificationLevel(VerificationLevel.NONE);
        userProfileRepository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileSnapshot getRequiredProfile(UUID userId) {
        return toSnapshot(loadProfile(userId));
    }

    @Override
    @Transactional
    public UserProfileSnapshot updateVerificationStatus(UUID userId, VerificationStatus status) {
        UserProfile profile = loadProfile(userId);
        applyVerificationStatus(profile, status);
        userProfileRepository.save(profile);
        return toSnapshot(profile);
    }

    private void applyVerificationStatus(UserProfile profile, VerificationStatus status) {
        switch (status) {
            case PENDING -> {
                profile.setVerificationLevel(VerificationLevel.PENDING);
                profile.setVerified(false);
            }
            case APPROVED -> {
                profile.setVerificationLevel(VerificationLevel.VERIFIED);
                profile.setVerified(true);
            }
            case REJECTED -> {
                profile.setVerificationLevel(VerificationLevel.REJECTED);
                profile.setVerified(false);
            }
        }
    }

    private UserProfile loadProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    private UserProfileSnapshot toSnapshot(UserProfile profile) {
        return new UserProfileSnapshot(
                profile.getUserId(),
                profile.isVerified(),
                profile.getVerificationLevel()
        );
    }
}
