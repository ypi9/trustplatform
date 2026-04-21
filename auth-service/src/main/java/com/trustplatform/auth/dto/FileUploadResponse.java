package com.trustplatform.auth.dto;

import com.trustplatform.auth.storage.dto.S3UploadResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String fileUrl;
    private String bucket;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private long size;

    public static FileUploadResponse from(S3UploadResult result) {
        return FileUploadResponse.builder()
                .fileUrl(result.getObjectKey())
                .bucket(result.getBucket())
                .objectKey(result.getObjectKey())
                .originalFilename(result.getOriginalFilename())
                .contentType(result.getContentType())
                .size(result.getSize())
                .build();
    }
}
