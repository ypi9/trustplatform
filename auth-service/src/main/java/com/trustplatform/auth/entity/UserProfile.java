package com.trustplatform.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Data
public class UserProfile {
    @Id
    private UUID userId;

    private String fullName;
    private String phone;
    private boolean isVerified;

    @Enumerated(EnumType.STRING)
    private VerificationLevel verificationLevel;
}
