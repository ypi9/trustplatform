package com.trustplatform.auth.user.service;

import java.util.UUID;

public interface UserLookupClient {

    UserLookup getUserById(UUID userId);

    UserLookup getUserByEmail(String email);

    record UserLookup(UUID id, String email, String role) {
    }
}
