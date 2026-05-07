package com.trustplatform.auth.user.service;

import com.trustplatform.auth.user.entity.User;
import com.trustplatform.auth.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class LocalUserLookupService implements UserLookupClient {

    private final UserRepository userRepository;

    public LocalUserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserLookup getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toLookup(user);
    }

    @Override
    public UserLookup getUserByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toLookup(user);
    }

    private UserLookup toLookup(User user) {
        return new UserLookup(user.getId(), user.getEmail(), user.getRole());
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
