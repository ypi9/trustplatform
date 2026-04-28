package com.trustplatform.auth.auth.controller;

import com.trustplatform.auth.auth.dto.response.AuthResponse;
import com.trustplatform.auth.auth.dto.request.LoginRequest;
import com.trustplatform.auth.auth.dto.request.SignupRequest;
import com.trustplatform.auth.user.dto.response.UserResponse;
import com.trustplatform.auth.auth.service.AuthService;
import com.trustplatform.auth.common.api.ApiSuccessResponse;
import com.trustplatform.auth.common.api.ApiSuccessResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final ApiSuccessResponseFactory successResponseFactory;

    public AuthController(AuthService authService, ApiSuccessResponseFactory successResponseFactory) {
        this.authService = authService;
        this.successResponseFactory = successResponseFactory;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiSuccessResponse<String>> signup(@Valid @RequestBody SignupRequest request) {
        String message = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(successResponseFactory.build(message));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiSuccessResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(successResponseFactory.build(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiSuccessResponse<UserResponse>> me(Authentication authentication) {
        UserResponse response = authService.getProfile(authentication.getName());
        return ResponseEntity.ok(successResponseFactory.build(response));
    }
}
