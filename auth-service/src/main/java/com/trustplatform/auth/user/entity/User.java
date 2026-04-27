package com.trustplatform.auth.user.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@Table(name = "users")
public class User {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private String role;

    @PrePersist
    public void prePersist() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        if (this.role == null) {
            this.role = "USER";
        }
    }
}