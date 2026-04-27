package com.trustplatform.auth.storage.dto;

import com.trustplatform.auth.storage.dto.S3UploadResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String fileUrl;
    private String bucket;
    private String objectKey;
    private UUID requestId;
    private String originalFilename;
    private String contentType;
    private long size;

    public static FileUploadResponse from(S3UploadResult result) {
        return FileUploadResponse.builder()
                .fileUrl(result.getObjectKey())
                .bucket(result.getBucket())
                .objectKey(result.getObjectKey())
                .requestId(result.getRequestId())
                .originalFilename(result.getOriginalFilename())
                .contentType(result.getContentType())
                .size(result.getSize())
                .build();
    }
}
