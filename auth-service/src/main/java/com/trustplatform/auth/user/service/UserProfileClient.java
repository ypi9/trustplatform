package com.trustplatform.auth.user.service;

import java.util.UUID;

public interface UserProfileClient {

    void createEmptyProfile(UUID userId);

    UserProfileSnapshot getRequiredProfile(UUID userId);
}
