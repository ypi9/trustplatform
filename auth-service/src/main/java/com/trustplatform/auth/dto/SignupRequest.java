package com.trustplatform.auth.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String email;
    private String password;
}