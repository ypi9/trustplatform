package com.trustplatform.auth.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustplatform.auth.auth.security.AuthenticatedUser;
import com.trustplatform.auth.common.logging.StructuredLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ApiErrorResponseFactory errorResponseFactory;
    private final StructuredLogService structuredLogService;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper, ApiErrorResponseFactory errorResponseFactory,
                                   StructuredLogService structuredLogService) {
        this.objectMapper = objectMapper;
        this.errorResponseFactory = errorResponseFactory;
        this.structuredLogService = structuredLogService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", request.getRequestURI());
        metadata.put("method", request.getMethod());
        metadata.put("status", HttpStatus.FORBIDDEN.value());
        metadata.put("errorDetail", accessDeniedException.getMessage());
        structuredLogService.logWarn(
                "access_denied",
                "Access denied",
                currentUserId(),
                metadata
        );
        writeError(response, errorResponseFactory.build(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource",
                request
        ));
    }

    private void writeError(HttpServletResponse response, ApiErrorResponse errorResponse) throws IOException {
        response.setStatus(errorResponse.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.getId();
    }
}
