package com.trustplatform.auth.storage.service;

import com.trustplatform.auth.storage.dto.S3BucketInfo;
import com.trustplatform.auth.storage.dto.S3UploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service that wraps AWS S3 operations.
 *
 * <p>Provides bucket-level health checks and validated object uploads.</p>
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "application/pdf"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3StorageService(S3Client s3Client,
                            S3Presigner s3Presigner,
                            @Qualifier("s3BucketName") String bucketName) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    /**
     * Validates and uploads a multipart file to S3.
     *
     * @param file multipart file from the request
     * @return uploaded object metadata
     */
    public S3UploadResult upload(MultipartFile file) {
        validateFile(file);

        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        String contentType = file.getContentType();
        String objectKey = generateObjectKey(contentType);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(file.getSize())
                .metadata(Map.of(
                        "original-filename", originalFilename,
                        "size", String.valueOf(file.getSize())
                ))
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read uploaded file");
        } catch (S3Exception e) {
            log.error("S3 upload failed for bucket '{}' key '{}': {}", bucketName, objectKey, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store file. Please try again.");
        }

        log.info("Uploaded file '{}' to S3 bucket '{}' with key '{}'", originalFilename, bucketName, objectKey);

        return S3UploadResult.builder()
                .bucket(bucketName)
                .objectKey(objectKey)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .size(file.getSize())
                .build();
    }

    /**
     * Checks whether an object exists in the configured bucket.
     */
    public boolean objectExists(String objectKey) {
        String normalizedKey = normalizeObjectKey(objectKey);
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return false;
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizedKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.warn("S3 object check failed for bucket '{}' key '{}': {}",
                    bucketName, normalizedKey, e.getMessage());
            return false;
        }
    }

    /**
     * Reads trusted object metadata from S3.
     */
    public S3UploadResult getObjectMetadata(String objectKey) {
        String normalizedKey = normalizeObjectKey(objectKey);
        if (normalizedKey == null || normalizedKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentKey is required");
        }

        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizedKey)
                    .build());

            return S3UploadResult.builder()
                    .bucket(bucketName)
                    .objectKey(normalizedKey)
                    .originalFilename(response.metadata().get("original-filename"))
                    .contentType(response.contentType())
                    .size(response.contentLength() != null ? response.contentLength() : 0)
                    .build();
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File not found. Please upload a file first via POST /files/upload");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "File not found. Please upload a file first via POST /files/upload");
            }
            log.warn("S3 metadata lookup failed for bucket '{}' key '{}': {}",
                    bucketName, normalizedKey, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not verify uploaded file. Please try again.");
        }
    }

    /**
     * Generates a short-lived GET URL for a private S3 object.
     */
    public URL generatePresignedGetUrl(String objectKey, Duration expiresIn) {
        String normalizedKey = normalizeObjectKey(objectKey);
        if (normalizedKey == null || normalizedKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentKey is required");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(normalizedKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url();
    }

    /**
     * Returns true when S3 Block Public Access is enabled at the bucket level.
     */
    public boolean isBucketPublicAccessBlocked() {
        try {
            PublicAccessBlockConfiguration config = s3Client.getPublicAccessBlock(
                    GetPublicAccessBlockRequest.builder()
                            .bucket(bucketName)
                            .build()
            ).publicAccessBlockConfiguration();

            return Boolean.TRUE.equals(config.blockPublicAcls())
                    && Boolean.TRUE.equals(config.ignorePublicAcls())
                    && Boolean.TRUE.equals(config.blockPublicPolicy())
                    && Boolean.TRUE.equals(config.restrictPublicBuckets());
        } catch (NoSuchPublicAccessBlockConfigurationException e) {
            log.warn("S3 bucket '{}' does not have bucket-level Block Public Access configured", bucketName);
            return false;
        } catch (S3Exception e) {
            log.warn("Could not verify S3 Block Public Access for bucket '{}': {}", bucketName, e.getMessage());
            return false;
        }
    }

    // ──────────────────────────────────────────────
    //  Bucket health / validation
    // ──────────────────────────────────────────────

    /**
     * Validates that the configured S3 bucket exists and is accessible,
     * then returns metadata including a sample of object keys.
     *
     * @param maxKeys maximum number of sample keys to return (capped at 20)
     * @return {@link S3BucketInfo} with accessibility status and sample keys
     */
    public S3BucketInfo validateBucketAccess(int maxKeys) {
        int limit = Math.min(maxKeys, 20);

        try {
            // HEAD bucket — fast existence + permission check
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());

            // List a small sample of objects
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .maxKeys(limit)
                            .build());

            List<String> sampleKeys = listResponse.contents().stream()
                    .map(S3Object::key)
                    .toList();

            log.info("S3 bucket '{}' is accessible — {} object(s) sampled",
                    bucketName, sampleKeys.size());

            return S3BucketInfo.builder()
                    .bucket(bucketName)
                    .region(s3Client.serviceClientConfiguration()
                            .region().id())
                    .accessible(true)
                    .objectCount(listResponse.keyCount())
                    .sampleKeys(sampleKeys)
                    .checkedAt(Instant.now())
                    .build();

        } catch (NoSuchBucketException e) {
            log.error("S3 bucket '{}' does not exist", bucketName);
            return errorInfo("Bucket does not exist: " + bucketName);

        } catch (S3Exception e) {
            log.error("S3 error accessing bucket '{}': {}", bucketName, e.getMessage());
            return errorInfo(e.getMessage());
        }
    }

    /**
     * Quick health check — returns {@code true} if the bucket responds to HEAD.
     */
    public boolean isBucketAccessible() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            return true;
        } catch (S3Exception e) {
            log.warn("S3 bucket '{}' is not accessible: {}", bucketName, e.getMessage());
            return false;
        }
    }

    /**
     * Lists object keys in the bucket, optionally filtered by a prefix.
     *
     * @param prefix key prefix filter (e.g. "uploads/"), or {@code null} for all
     * @param maxKeys maximum number of keys to return
     * @return list of matching object keys
     */
    public List<String> listObjects(String prefix, int maxKeys) {
        ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .maxKeys(maxKeys);

        if (prefix != null && !prefix.isBlank()) {
            request.prefix(prefix);
        }

        ListObjectsV2Response response = s3Client.listObjectsV2(request.build());

        return response.contents().stream()
                .map(S3Object::key)
                .toList();
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private S3BucketInfo errorInfo(String message) {
        return S3BucketInfo.builder()
                .bucket(bucketName)
                .accessible(false)
                .error(message)
                .checkedAt(Instant.now())
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File size exceeds the 5 MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File type not allowed. Accepted types: png, jpg, pdf");
        }

        validateFileSignature(file, contentType);
    }

    private String generateObjectKey(String contentType) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String extension = extensionForContentType(contentType);

        return "uploads/%d/%02d/%02d/%s%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

    private String cleanOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload";
        }

        String filename = originalFilename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        filename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            return "upload";
        }
        return filename.length() > MAX_ORIGINAL_FILENAME_LENGTH
                ? filename.substring(0, MAX_ORIGINAL_FILENAME_LENGTH)
                : filename;
    }

    private String extensionForContentType(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "application/pdf" -> ".pdf";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File type not allowed. Accepted types: png, jpg, pdf");
        };
    }

    private void validateFileSignature(MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(8);
            boolean valid = switch (contentType) {
                case "image/png" -> header.length >= 8
                        && (header[0] & 0xFF) == 0x89
                        && header[1] == 0x50
                        && header[2] == 0x4E
                        && header[3] == 0x47
                        && header[4] == 0x0D
                        && header[5] == 0x0A
                        && header[6] == 0x1A
                        && header[7] == 0x0A;
                case "image/jpeg" -> header.length >= 3
                        && (header[0] & 0xFF) == 0xFF
                        && (header[1] & 0xFF) == 0xD8
                        && (header[2] & 0xFF) == 0xFF;
                case "application/pdf" -> header.length >= 4
                        && header[0] == 0x25
                        && header[1] == 0x50
                        && header[2] == 0x44
                        && header[3] == 0x46;
                default -> false;
            };

            if (!valid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "File content does not match declared content type");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read uploaded file");
        }
    }

    public String normalizeObjectKey(String objectKey) {
        if (objectKey == null) {
            return null;
        }

        String normalized = objectKey.strip();
        if (normalized.startsWith("s3://" + bucketName + "/")) {
            normalized = normalized.substring(("s3://" + bucketName + "/").length());
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..") || normalized.startsWith("http://") || normalized.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid documentKey");
        }
        return normalized;
    }
}
