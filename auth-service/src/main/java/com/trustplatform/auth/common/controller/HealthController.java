package com.trustplatform.auth.common.controller;

import com.trustplatform.auth.storage.service.S3StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final S3StorageService s3StorageService;

    public HealthController(DataSource dataSource, S3StorageService s3StorageService) {
        this.dataSource = dataSource;
        this.s3StorageService = s3StorageService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "auth-service",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean databaseUp = isDatabaseReachable();
        boolean s3Up = s3StorageService.isBucketAccessible();
        boolean ready = databaseUp && s3Up;

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", databaseUp ? "UP" : "DOWN");
        checks.put("s3", s3Up ? "UP" : "DOWN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ready ? "UP" : "DOWN");
        body.put("service", "auth-service");
        body.put("checks", checks);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    private boolean isDatabaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }
}
