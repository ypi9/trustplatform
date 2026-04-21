package com.trustplatform.auth.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3UploadResult {
    private String bucket;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private long size;
}
