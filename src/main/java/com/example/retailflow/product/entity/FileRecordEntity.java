package com.example.retailflow.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("file_record")
public class FileRecordEntity {
    private Long id;
    private String bizType;
    private Long bizId;
    private String fileName;
    private String storagePlatform;
    private String bucketName;
    private String objectKey;
    private String fileUrl;
    private String contentType;
    private Long fileSize;
    private java.time.LocalDateTime createdAt;
}
