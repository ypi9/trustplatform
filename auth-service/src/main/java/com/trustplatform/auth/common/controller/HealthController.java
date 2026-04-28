package com.trustplatform.auth.common.controller;

import com.trustplatform.auth.storage.service.S3StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
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
    public ResponseEntity<Map<String, String>> health() {
        return dependencyHealthResponse(false);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        return dependencyHealthResponse(true);
    }

    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    private ResponseEntity<Map<String, String>> dependencyHealthResponse(boolean failWhenDown) {
        boolean databaseUp = isDatabaseReachable();
        boolean s3Up = s3StorageService.isBucketAccessible();
        boolean up = databaseUp && s3Up;

        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DOWN");
        body.put("database", databaseUp ? "UP" : "DOWN");
        body.put("s3", s3Up ? "UP" : "DOWN");

        HttpStatus status = !up && failWhenDown ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(body);
    }

    private boolean isDatabaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }
}
