package com.trustplatform.auth.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class AppMetricsService {

    private final Counter loginCounter;
    private final Counter verificationRequestCounter;
    private final Counter verificationApprovalCounter;
    private final Counter verificationRejectionCounter;

    public AppMetricsService(MeterRegistry meterRegistry) {
        this.loginCounter = Counter.builder("trustplatform.auth.logins")
                .description("Number of successful logins")
                .register(meterRegistry);
        this.verificationRequestCounter = Counter.builder("trustplatform.verification.requests")
                .description("Number of verification requests submitted")
                .register(meterRegistry);
        this.verificationApprovalCounter = Counter.builder("trustplatform.verification.approvals")
                .description("Number of verification requests approved")
                .register(meterRegistry);
        this.verificationRejectionCounter = Counter.builder("trustplatform.verification.rejections")
                .description("Number of verification requests rejected")
                .register(meterRegistry);
    }

    public void incrementLogins() {
        loginCounter.increment();
    }

    public void incrementVerificationRequests() {
        verificationRequestCounter.increment();
    }

    public void incrementVerificationApprovals() {
        verificationApprovalCounter.increment();
    }

    public void incrementVerificationRejections() {
        verificationRejectionCounter.increment();
    }
}
