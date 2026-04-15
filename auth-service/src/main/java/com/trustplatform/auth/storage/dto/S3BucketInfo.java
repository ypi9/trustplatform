package com.trustplatform.auth.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO returned by the bucket-validation endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3BucketInfo {
    private String bucket;
    private String region;
    private boolean accessible;
    private int objectCount;
    private List<String> sampleKeys;
    private Instant checkedAt;
    private String error;
}
