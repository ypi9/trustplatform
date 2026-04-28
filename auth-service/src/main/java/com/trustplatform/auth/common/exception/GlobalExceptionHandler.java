package com.trustplatform.auth.common.exception;

import com.trustplatform.auth.auth.security.AuthenticatedUser;
import com.trustplatform.auth.common.logging.StructuredLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ApiErrorResponseFactory errorResponseFactory;
    private final StructuredLogService structuredLogService;

    public GlobalExceptionHandler(ApiErrorResponseFactory errorResponseFactory,
                                  StructuredLogService structuredLogService) {
        this.errorResponseFactory = errorResponseFactory;
        this.structuredLogService = structuredLogService;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        structuredLogService.logWarn(
                "access_denied",
                "Access denied",
                currentUserId(),
                baseMetadata(request, HttpStatus.FORBIDDEN, ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorResponseFactory.build(HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource", request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException ex,
                                                                          HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        Map<String, Object> metadata = baseMetadata(request, status, ex.getReason());
        if (status.is5xxServerError()) {
            structuredLogService.logError("request_failed", "Request failed", currentUserId(), metadata);
        } else {
            structuredLogService.logWarn("request_failed", "Request failed", currentUserId(), metadata);
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
        Map<String, Object> metadata = baseMetadata(request, HttpStatus.BAD_REQUEST, "Validation failed");
        metadata.put("fields", fieldErrors);
        structuredLogService.logWarn("validation_failed", "Request validation failed", currentUserId(), metadata);

        return ResponseEntity.badRequest()
                .body(errorResponseFactory.build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException ex,
                                                                    HttpServletRequest request) {
        structuredLogService.logWarn(
                "malformed_request",
                "Malformed request body",
                currentUserId(),
                baseMetadata(request, HttpStatus.BAD_REQUEST, ex.getMessage())
        );
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

        Map<String, Object> metadata = baseMetadata(request, HttpStatus.CONFLICT, message);
        metadata.put("rootCause", rootMessage);
        structuredLogService.logWarn(
                "data_integrity_violation",
                "Data integrity violation",
                currentUserId(),
                metadata
        );

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorResponseFactory.build(HttpStatus.CONFLICT, message, request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
                                                                        HttpServletRequest request) {
        structuredLogService.logWarn(
                "upload_too_large",
                "Multipart request exceeded configured upload limit",
                currentUserId(),
                baseMetadata(request, HttpStatus.BAD_REQUEST, ex.getMessage())
        );
        return ResponseEntity.badRequest()
                .body(errorResponseFactory.build(HttpStatus.BAD_REQUEST,
                        "File size exceeds the 5 MB limit", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericError(Exception ex, HttpServletRequest request) {
        structuredLogService.logError(
                "unexpected_error",
                "Unhandled application error",
                currentUserId(),
                baseMetadata(request, HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponseFactory.build(HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred", request));
    }

    private Map<String, Object> baseMetadata(HttpServletRequest request, HttpStatus status, String errorDetail) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", request.getRequestURI());
        metadata.put("method", request.getMethod());
        metadata.put("status", status.value());
        if (errorDetail != null && !errorDetail.isBlank()) {
            metadata.put("errorDetail", errorDetail);
        }
        return metadata;
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.getId();
    }
}
