package com.trustplatform.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RuntimeDiagnosticsConfig {

    private static final Logger log = LoggerFactory.getLogger(RuntimeDiagnosticsConfig.class);

    private final Environment environment;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private String serverPort;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.s3.bucket}")
    private String s3Bucket;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private String maxRequestSize;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public RuntimeDiagnosticsConfig(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupSummary() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profiles = activeProfiles.length == 0 ? "default" : String.join(", ", activeProfiles);

        log.info("Application '{}' started successfully", applicationName);
        log.info("Active profile(s): {}", profiles);
        log.info(
                "Runtime config summary: server.port={}, aws.region={}, aws.s3.bucket={}, multipart.max-file-size={}, multipart.max-request-size={}, datasource={}",
                serverPort,
                awsRegion,
                s3Bucket,
                maxFileSize,
                maxRequestSize,
                sanitizeJdbcUrl(datasourceUrl)
        );
    }

    private String sanitizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "not-configured";
        }

        int schemeSeparator = jdbcUrl.indexOf("://");
        if (schemeSeparator < 0) {
            return "configured";
        }

        int hostStart = schemeSeparator + 3;
        int pathStart = jdbcUrl.indexOf('/', hostStart);
        if (pathStart < 0) {
            return "configured";
        }

        return jdbcUrl.substring(0, pathStart + 1) + "<db>";
    }
}
