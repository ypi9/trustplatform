package com.trustplatform.auth.repository;

import com.trustplatform.auth.entity.VerificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, UUID> {

    // Get all verification requests for a user, newest first
    List<VerificationRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Get the most recent verification request for a user
    Optional<VerificationRequest> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
}
