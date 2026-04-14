package com.trustplatform.auth.controller;

import com.trustplatform.auth.dto.AuthResponse;
import com.trustplatform.auth.dto.LoginRequest;
import com.trustplatform.auth.dto.SignupRequest;
import com.trustplatform.auth.dto.UserResponse;
import com.trustplatform.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        String message = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        UserResponse response = authService.getProfile(authentication.getName());
        return ResponseEntity.ok(response);
    }
}