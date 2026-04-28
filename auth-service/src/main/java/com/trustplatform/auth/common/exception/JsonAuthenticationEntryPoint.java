package com.trustplatform.auth.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustplatform.auth.common.logging.StructuredLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final ApiErrorResponseFactory errorResponseFactory;
    private final StructuredLogService structuredLogService;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper, ApiErrorResponseFactory errorResponseFactory,
                                        StructuredLogService structuredLogService) {
        this.objectMapper = objectMapper;
        this.errorResponseFactory = errorResponseFactory;
        this.structuredLogService = structuredLogService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", request.getRequestURI());
        metadata.put("method", request.getMethod());
        metadata.put("status", HttpStatus.UNAUTHORIZED.value());
        metadata.put("errorDetail", authException.getMessage());
        structuredLogService.logWarn(
                "authentication_failed",
                "Authentication is required",
                null,
                metadata
        );
        writeError(response, errorResponseFactory.build(
                HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource",
                request
        ));
    }

    private void writeError(HttpServletResponse response, ApiErrorResponse errorResponse) throws IOException {
        response.setStatus(errorResponse.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
