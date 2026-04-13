package com.trustplatform.auth.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Handles file upload, validation, and storage.
 *
 * Allowed content types: image/png, image/jpeg, application/pdf
 * Max file size: enforced by Spring multipart config (5 MB)
 * Storage location: configurable via file.upload-dir (default: uploads)
 */
@Service
public class FileService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "application/pdf"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path uploadPath;

    /**
     * Creates the upload directory on startup if it doesn't exist.
     */
    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadPath, e);
        }
    }

    /**
     * Validates and stores an uploaded file.
     *
     * @param file the multipart file from the request
     * @return the relative URL path to the stored file, e.g. "/uploads/uuid-filename.png"
     * @throws ResponseStatusException 400 if file is empty, type not allowed, or size exceeded
     */
    public String storeFile(MultipartFile file) {
        // 1. Empty check
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        // 2. Size check (defense-in-depth; Spring multipart config is the first guard)
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File size exceeds the 5 MB limit");
        }

        // 3. Content type check
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File type not allowed. Accepted types: png, jpg, pdf");
        }

        // 4. Generate unique filename preserving the original extension
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + extension;

        // 5. Save file to disk
        try {
            Path targetLocation = uploadPath.resolve(storedFilename).normalize();

            // Security: prevent path traversal
            if (!targetLocation.startsWith(uploadPath)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not store file. Please try again.");
        }

        return "/uploads/" + storedFilename;
    }

    /**
     * Checks whether a previously uploaded file exists on disk.
     *
     * @param fileUrl the relative URL returned by storeFile, e.g. "/uploads/uuid.png"
     * @return true if the file exists and is a regular file
     */
    public boolean fileExists(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            return false;
        }
        String filename = fileUrl.substring("/uploads/".length());
        Path filePath = uploadPath.resolve(filename).normalize();
        return filePath.startsWith(uploadPath) && Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    /**
     * Extracts the file extension (including the dot) from the original filename.
     * Returns empty string if no extension found.
     */
    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex);
    }
}
