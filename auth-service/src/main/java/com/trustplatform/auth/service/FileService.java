package com.trustplatform.auth.service;

import com.trustplatform.auth.repository.UserRepository;
import com.trustplatform.auth.storage.dto.S3UploadResult;
import com.trustplatform.auth.storage.service.S3StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles file upload, validation, and storage.
 *
 * Allowed content types: image/png, image/jpeg, application/pdf
 * Max file size: enforced by Spring multipart config and S3StorageService (5 MB)
 * Storage location: configured S3 bucket
 */
@Service
public class FileService {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;

    public FileService(AuditLogService auditLogService, UserRepository userRepository,
                       S3StorageService s3StorageService) {
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.s3StorageService = s3StorageService;
    }

    /**
     * Validates and stores an uploaded file.
     *
     * @param file  the multipart file from the request
     * @param email the authenticated user's email (for audit logging)
     * @return S3 upload result
     * @throws ResponseStatusException 400 if file is empty, type not allowed, or size exceeded
     */
    public S3UploadResult storeFile(MultipartFile file, String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        UUID requestId = UUID.randomUUID();
        S3UploadResult uploadResult = s3StorageService.upload(file, user.getId(), requestId);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bucket", uploadResult.getBucket());
        metadata.put("objectKey", uploadResult.getObjectKey());
        metadata.put("requestId", uploadResult.getRequestId());
        metadata.put("originalName", uploadResult.getOriginalFilename());
        metadata.put("contentType", uploadResult.getContentType());
        metadata.put("size", uploadResult.getSize());
        auditLogService.log("file_uploaded", user.getId(), metadata);

        return uploadResult;
    }

    public S3UploadResult getFileMetadata(String documentKey) {
        return s3StorageService.getObjectMetadata(documentKey);
    }

    public URL generateDownloadUrl(String documentKey, Duration expiresIn) {
        return s3StorageService.generatePresignedGetUrl(documentKey, expiresIn);
    }

    public UUID extractRequestId(String documentKey) {
        return s3StorageService.extractRequestId(documentKey);
    }

    public boolean isOwnedByUser(String documentKey, UUID userId) {
        return s3StorageService.isOwnedByUser(documentKey, userId);
    }
}
