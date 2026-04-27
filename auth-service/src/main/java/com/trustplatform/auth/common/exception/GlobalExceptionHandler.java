package com.trustplatform.auth.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiErrorResponseFactory errorResponseFactory;

    public GlobalExceptionHandler(ApiErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorResponseFactory.build(HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource", request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex,
                                                                          HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        if (status.is5xxServerError()) {
            log.error("Request failed with {}: {}", status, ex.getReason(), ex);
        } else {
            log.warn("Request failed with {}: {}", status, ex.getReason());
        }
        return ResponseEntity.status(status)
                .body(errorResponseFactory.build(status, ex.getReason(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.badRequest()
                .body(errorResponseFactory.build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException ex,
                                                                    HttpServletRequest request) {
        log.warn("Malformed request body for {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(errorResponseFactory.build(HttpStatus.BAD_REQUEST, "Malformed request body", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                         HttpServletRequest request) {
        String message = "Data integrity violation";
        String rootMessage = ex.getMostSpecificCause().getMessage();

        if (rootMessage != null) {
            if (rootMessage.contains("users_email_key") || rootMessage.contains("email")) {
                message = "Email already exists";
            } else if (rootMessage.contains("verification_requests")) {
                message = "Verification request conflict";
            } else if (rootMessage.contains("user_profile")) {
                message = "User profile conflict";
            }
        }

        log.warn("Data integrity violation: {}", rootMessage);

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorResponseFactory.build(HttpStatus.CONFLICT, message, request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
                                                                        HttpServletRequest request) {
        log.warn("Multipart request exceeded configured upload limit", ex);
        return ResponseEntity.badRequest()
                .body(errorResponseFactory.build(HttpStatus.BAD_REQUEST,
                        "File size exceeds the 5 MB limit", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericError(Exception ex, HttpServletRequest request) {
        log.error("Unhandled application error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponseFactory.build(HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred", request));
    }
}
