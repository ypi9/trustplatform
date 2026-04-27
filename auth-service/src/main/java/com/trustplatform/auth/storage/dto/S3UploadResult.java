package com.trustplatform.auth.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3UploadResult {
    private String bucket;
    private String objectKey;
    private UUID requestId;
    private String originalFilename;
    private String contentType;
    private long size;
}
