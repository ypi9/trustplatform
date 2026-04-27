package com.trustplatform.auth.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        if (userId != null) {
            payload.put("userId", userId);
        }
        payload.putAll(metadata);
        eventLogger.info("{}", toJson(payload));
    }

    public void logRequest(String method, String path, int status, long durationMs, UUID userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", method);
        payload.put("path", path);
        payload.put("status", status);
        payload.put("durationMs", durationMs);
        if (userId != null) {
            payload.put("userId", userId);
        }
        requestLogger.info("{}", toJson(payload));
    }

    public String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"serializationError\":\"" + e.getClass().getSimpleName() + "\"}";
        }
    }
}
