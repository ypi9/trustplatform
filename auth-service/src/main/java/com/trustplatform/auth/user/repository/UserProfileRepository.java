package com.trustplatform.auth.user.repository;

import com.trustplatform.auth.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
