package com.trustplatform.auth.audit.repository;

import com.trustplatform.auth.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
