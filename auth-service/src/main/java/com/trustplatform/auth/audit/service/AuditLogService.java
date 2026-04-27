package com.trustplatform.auth.audit.service;

import com.trustplatform.auth.audit.entity.AuditLog;
import com.trustplatform.auth.common.logging.StructuredLogService;
import com.trustplatform.auth.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final StructuredLogService structuredLogService;

    public AuditLogService(AuditLogRepository auditLogRepository, StructuredLogService structuredLogService) {
        this.auditLogRepository = auditLogRepository;
        this.structuredLogService = structuredLogService;
    }

    public void log(String action, UUID userId, String metadata) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUserId(userId);
        log.setMetadata(metadata);
        auditLogRepository.save(log);
    }

    public void log(String action, UUID userId, Map<String, Object> metadata) {
        structuredLogService.logEvent(action, userId, metadata);
        log(action, userId, structuredLogService.toJson(metadata));
    }

    public void log(String action, String metadata) {
        log(action, null, metadata);
    }
}
