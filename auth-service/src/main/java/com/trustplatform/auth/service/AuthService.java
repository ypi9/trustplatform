package com.trustplatform.auth.service;

import com.trustplatform.auth.dto.AuthResponse;
import com.trustplatform.auth.dto.LoginRequest;
import com.trustplatform.auth.entity.User;
import com.trustplatform.auth.repository.UserRepository;
import com.trustplatform.auth.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
    }

    public AuthResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail()).orElse(null);

    // Case 1: user not found
    if (user == null) {
        auditLogService.log(
                "login_failed",
                null,
                "{\"email\":\"" + request.getEmail() + "\", \"reason\":\"user_not_found\"}"
        );
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    // Case 2: password mismatch
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
        auditLogService.log(
                "login_failed",
                user.getId(),
                "{\"email\":\"" + request.getEmail() + "\", \"reason\":\"wrong_password\"}"
        );
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    // Success
    auditLogService.log(
            "login_success",
            user.getId(),
            "{\"email\":\"" + request.getEmail() + "\"}"
    );

    String token = jwtService.generateToken(user);
    return new AuthResponse(token);
}
}
