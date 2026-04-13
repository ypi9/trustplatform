package com.trustplatform.auth.controller;

import com.trustplatform.auth.dto.FileUploadResponse;
import com.trustplatform.auth.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileUrl = fileService.storeFile(file);
        return ResponseEntity.ok(new FileUploadResponse(fileUrl));
    }
}
