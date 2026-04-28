package com.trustplatform.auth.common.logging;

import com.trustplatform.auth.auth.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final StructuredLogService structuredLogService;

    public RequestLoggingFilter(StructuredLogService structuredLogService) {
        this.structuredLogService = structuredLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = resolveRequestId(request);
        RequestCorrelation.setRequestId(requestId);
        response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, requestId);

        UUID userId = currentUserId();
        structuredLogService.logRequestReceived(
                request.getMethod(),
                request.getRequestURI(),
                userId
        );

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            structuredLogService.logRequestCompleted(
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    currentUserId()
            );
            RequestCorrelation.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(RequestCorrelation.REQUEST_ID_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.strip();
        }
        return UUID.randomUUID().toString();
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.getId();
    }
}
