package com.trustplatform.auth.service;

import com.trustplatform.auth.entity.AuditLog;
import com.trustplatform.auth.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String action, UUID userId, String metadata) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUserId(userId);
        log.setMetadata(metadata);
        auditLogRepository.save(log);
    }

    public void log(String action, String metadata) {
        log(action, null, metadata);
    }
}
