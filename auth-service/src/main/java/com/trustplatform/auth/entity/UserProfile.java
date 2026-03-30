package com.trustplatform.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class UserProfile {
    @Id
    private String userId;

    private String fullName;
    private String phone;
    private boolean isVerified;

    @Enumerated(EnumType.STRING)
    private VerificationLevel verificationLevel;

    public enum VerificationLevel {
        NONE, BASIC, ADVANCED
    }
}
