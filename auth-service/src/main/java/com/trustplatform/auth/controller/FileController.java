package com.trustplatform.auth.controller;

import com.trustplatform.auth.dto.FileUploadResponse;
import com.trustplatform.auth.repository.UserRepository;
import com.trustplatform.auth.service.AuditLogService;
import com.trustplatform.auth.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for file uploads.
 *
 * POST /files/upload — upload a file (requires Bearer token)
 *   Content-Type: multipart/form-data
 *   Form field: "file"
 *   Returns: { "fileUrl": "/uploads/uuid-filename.ext" }
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public FileController(FileService fileService, AuditLogService auditLogService, UserRepository userRepository) {
        this.fileService = fileService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        String fileUrl = fileService.storeFile(file);

        // Audit log
        String email = authentication.getName();
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        auditLogService.log("file_uploaded", user.getId(),
                "{\"fileUrl\":\"" + fileUrl
                + "\",\"originalName\":\"" + file.getOriginalFilename()
                + "\",\"size\":" + file.getSize() + "}");

        return ResponseEntity.ok(new FileUploadResponse(fileUrl));
    }
}
