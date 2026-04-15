package com.trustplatform.auth.storage.service;

import com.trustplatform.auth.storage.dto.S3BucketInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.util.List;

/**
 * Service that wraps AWS S3 operations.
 *
 * <p>Currently provides bucket-level health checks.
 * Will be extended with upload / download / delete methods
 * when the file-upload migration from local disk to S3 is implemented.</p>
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(S3Client s3Client,
                            @Qualifier("s3BucketName") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
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
}
