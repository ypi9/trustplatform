package com.trustplatform.auth.service;

import com.trustplatform.auth.repository.UserRepository;
import com.trustplatform.auth.storage.dto.S3UploadResult;
import com.trustplatform.auth.storage.service.S3StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
        S3UploadResult uploadResult = s3StorageService.upload(file);

        // Audit log
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        auditLogService.log("file_uploaded", user.getId(),
                "{\"bucket\":\"" + uploadResult.getBucket()
                + "\",\"objectKey\":\"" + uploadResult.getObjectKey()
                + "\",\"originalName\":\"" + uploadResult.getOriginalFilename()
                + "\",\"contentType\":\"" + uploadResult.getContentType()
                + "\",\"size\":" + uploadResult.getSize() + "}");

        return uploadResult;
    }

    /**
     * Checks whether a previously uploaded file exists in S3.
     *
     * @param fileUrl the S3 object key returned by storeFile
     * @return true if the object exists in S3
     */
    public boolean fileExists(String fileUrl) {
        return s3StorageService.objectExists(fileUrl);
    }
}
