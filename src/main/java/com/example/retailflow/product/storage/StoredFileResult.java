package com.example.retailflow.product.storage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoredFileResult {
    private String storagePlatform;
    private String bucketName;
    private String objectKey;
    private String fileUrl;
    private String contentType;
    private Long fileSize;
}
