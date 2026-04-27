package com.trustplatform.auth.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private String userId;
    private String email;
    private boolean isVerified;
    private String verificationLevel;
}
