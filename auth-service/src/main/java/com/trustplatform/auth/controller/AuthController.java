package com.trustplatform.auth.controller;

import com.trustplatform.auth.dto.AuthResponse;
import com.trustplatform.auth.dto.LoginRequest;
import com.trustplatform.auth.dto.SignupRequest;
import com.trustplatform.auth.dto.UserResponse;
import com.trustplatform.auth.entity.User;
import com.trustplatform.auth.entity.UserProfile;
import com.trustplatform.auth.repository.UserProfileRepository;
import com.trustplatform.auth.repository.UserRepository;
import com.trustplatform.auth.service.AuthService;
import com.trustplatform.auth.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final UserProfileRepository userProfileRepository;
    private final AuditLogService auditLogService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthService authService, UserProfileRepository userProfileRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.userProfileRepository = userProfileRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        // Create UserProfile with defaults
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setFullName("");
        profile.setPhone("");
        profile.setVerified(false);
        profile.setVerificationLevel(UserProfile.VerificationLevel.NONE);
        userProfileRepository.save(profile);

        auditLogService.log("user_registered", user.getId(), "{\"email\":\"" + user.getEmail() + "\"}");

        return ResponseEntity.ok("User created");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        UserResponse response = new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                profile.isVerified(),
                profile.getVerificationLevel().name()
        );

        return ResponseEntity.ok(response);
    }
}