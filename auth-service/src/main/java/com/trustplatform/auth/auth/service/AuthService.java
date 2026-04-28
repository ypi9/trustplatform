package com.trustplatform.auth.auth.service;

import com.trustplatform.auth.audit.service.AuditLogService;
import com.trustplatform.auth.auth.dto.response.AuthResponse;
import com.trustplatform.auth.auth.dto.request.LoginRequest;
import com.trustplatform.auth.auth.dto.request.SignupRequest;
import com.trustplatform.auth.common.metrics.AppMetricsService;
import com.trustplatform.auth.user.dto.response.UserResponse;
import com.trustplatform.auth.user.entity.User;
import com.trustplatform.auth.user.entity.UserProfile;
import com.trustplatform.auth.verification.entity.VerificationLevel;
import com.trustplatform.auth.user.repository.UserProfileRepository;
import com.trustplatform.auth.user.repository.UserRepository;
import com.trustplatform.auth.auth.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final AppMetricsService appMetricsService;

    public AuthService(UserRepository userRepository, UserProfileRepository userProfileRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AuditLogService auditLogService,
                       AppMetricsService appMetricsService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.appMetricsService = appMetricsService;
    }

    @Transactional
    public String signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setFullName("");
        profile.setPhone("");
        profile.setVerified(false);
        profile.setVerificationLevel(VerificationLevel.NONE);
        userProfileRepository.save(profile);

        auditLogService.log("user_registered", user.getId(), Map.of(
                "email", user.getEmail()
        ));

        return "User created";
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            auditLogService.log("login_failed", null, Map.of(
                    "email", normalizedEmail,
                    "reason", "user_not_found"
            ));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditLogService.log("login_failed", user.getId(), Map.of(
                    "email", normalizedEmail,
                    "reason", "wrong_password"
            ));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("email", normalizedEmail);
        metadata.put("role", user.getRole());
        auditLogService.log("login_success", user.getId(), metadata);
        appMetricsService.incrementLogins();

        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }

    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                profile.isVerified(),
                profile.getVerificationLevel().name()
        );
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
