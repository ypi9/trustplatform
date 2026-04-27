package com.trustplatform.auth.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class ApiErrorResponseFactory {

    public ApiErrorResponse build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, request, null);
    }

    public ApiErrorResponse build(HttpStatus status, String message, HttpServletRequest request,
                                  Map<String, String> fields) {
        return ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .fields(fields == null || fields.isEmpty() ? null : fields)
                .build();
    }
}
