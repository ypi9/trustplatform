package com.trustplatform.auth.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class StructuredLogService {

    private static final Logger eventLogger = LoggerFactory.getLogger("trustplatform.events");
    private static final Logger requestLogger = LoggerFactory.getLogger("trustplatform.requests");

    private final ObjectMapper objectMapper;

    public StructuredLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void logEvent(String action, UUID userId, Map<String, Object> metadata) {
        logEvent(action, defaultMessage(action), userId, metadata);
    }

    public void logEvent(String action, String message, UUID userId, Map<String, Object> metadata) {
        eventLogger.info("{}", toJson(buildPayload("INFO", action, message, userId, metadata)));
    }

    public void logRequestReceived(String method, String path, UUID userId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", method);
        metadata.put("path", path);
        requestLogger.info("{}", toJson(buildPayload(
                "INFO",
                "request_received",
                "Incoming request received",
                userId,
                metadata
        )));
    }

    public void logRequestCompleted(String method, String path, int status, long durationMs, UUID userId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", method);
        metadata.put("path", path);
        metadata.put("status", status);
        metadata.put("durationMs", durationMs);
        requestLogger.info("{}", toJson(buildPayload(
                "INFO",
                "request_completed",
                "Request completed",
                userId,
                metadata
        )));
    }

    public void logWarn(String action, String message, UUID userId, Map<String, Object> metadata) {
        eventLogger.warn("{}", toJson(buildPayload("WARN", action, message, userId, metadata)));
    }

    public void logError(String action, String message, UUID userId, Map<String, Object> metadata) {
        eventLogger.error("{}", toJson(buildPayload("ERROR", action, message, userId, metadata)));
    }

    private Map<String, Object> buildPayload(String level, String action, String message, UUID userId,
                                             Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("level", level);
        payload.put("action", action);
        payload.put("requestId", RequestCorrelation.getRequestId());
        if (userId != null) {
            payload.put("userId", userId);
        }
        payload.put("message", message);
        if (metadata != null) {
            payload.putAll(metadata);
        }
        return payload;
    }

    private String defaultMessage(String action) {
        return switch (action) {
            case "user_registered" -> "User registration successful";
            case "login_success" -> "User login successful";
            case "login_failed" -> "User login failed";
            case "verification_submitted" -> "Verification request submitted";
            case "verification_approved" -> "Verification request approved";
            case "verification_rejected" -> "Verification request rejected";
            case "file_uploaded" -> "Verification document uploaded";
            case "document_link_generated" -> "Verification document link generated";
            default -> action.replace('_', ' ');
        };
    }

    public String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"serializationError\":\"" + e.getClass().getSimpleName() + "\"}";
        }
    }
}
