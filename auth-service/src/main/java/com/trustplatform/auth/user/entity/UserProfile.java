package com.trustplatform.auth.user.entity;

import com.trustplatform.auth.verification.entity.VerificationLevel;
import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Data
public class UserProfile {
    @Id
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private boolean isVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationLevel verificationLevel;
}
