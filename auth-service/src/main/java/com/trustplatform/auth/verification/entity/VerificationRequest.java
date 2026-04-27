package com.trustplatform.auth.verification.entity;

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

    @Column(nullable = false, name = "document_url")
    private String documentUrl;

    @Column(nullable = false, name = "document_key")
    private String documentKey;

    @Column(nullable = false, name = "document_original_name")
    private String documentOriginalName;

    @Column(nullable = false, name = "document_content_type")
    private String documentContentType;

    @Column(nullable = false, name = "document_size")
    private Long documentSize;

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
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = Instant.now();
    }
}
