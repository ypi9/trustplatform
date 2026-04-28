package com.trustplatform.auth.verification.repository;

import com.trustplatform.auth.verification.entity.VerificationRequest;
import com.trustplatform.auth.verification.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, UUID> {

    // Get all verification requests for a user, newest first
    List<VerificationRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Get the most recent verification request for a user
    Optional<VerificationRequest> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    // Get all requests, newest first
    Page<VerificationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Filter by status, newest first
    Page<VerificationRequest> findByStatusOrderByCreatedAtDesc(VerificationStatus status, Pageable pageable);
}
