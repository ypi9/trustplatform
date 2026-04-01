package com.trustplatform.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "verification_requests")
/*
 * Verification Level Transition Rules:
 *
 *   NONE → PENDING → VERIFIED
 *   NONE → PENDING → REJECTED
 *   REJECTED → PENDING → VERIFIED
 *
 * Constraints:
 *   - VERIFIED users cannot submit another request
 *   - PENDING users cannot submit another request until reviewed
 *   - Only NONE or REJECTED users can submit a new request
 */
public class VerificationRequest {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant reviewedAt;

    private String reviewedBy;

    private String reviewNotes;

    @PrePersist
    public void prePersist() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
