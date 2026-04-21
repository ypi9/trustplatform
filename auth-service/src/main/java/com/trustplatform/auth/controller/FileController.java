package com.trustplatform.auth.controller;

import com.trustplatform.auth.dto.FileUploadResponse;
import com.trustplatform.auth.service.FileService;
import com.trustplatform.auth.storage.dto.S3UploadResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for file uploads.
 *
 * POST /files/upload — upload a file (requires Bearer token)
 *   Content-Type: multipart/form-data
 *   Form field: "file"
 *   Returns S3 object metadata, with fileUrl kept as the object key for compatibility.
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        S3UploadResult uploadResult = fileService.storeFile(file, authentication.getName());
        return ResponseEntity.ok(FileUploadResponse.from(uploadResult));
    }
}
