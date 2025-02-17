package com.example.retailflow.product.storage;

import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "minio")
public class MinioProductMediaStorageService implements ProductMediaStorageService {

    private final MinioClient minioClient;

    @Value("${app.storage.minio.bucket}")
    private String bucket;

    @Value("${app.storage.minio.public-endpoint}")
    private String publicEndpoint;

    @Override
    public StoredFileResult storeSkuImage(Long skuId, MultipartFile file) {
        String objectKey = "product/sku/" + skuId + "/" + System.currentTimeMillis() + "-" + sanitize(file.getOriginalFilename());
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return StoredFileResult.builder()
                    .storagePlatform("MINIO")
                    .bucketName(bucket)
                    .objectKey(objectKey)
                    .fileUrl(buildPublicUrl(objectKey))
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();
        } catch (Exception ex) {
            log.error("store minio sku image failed, skuId={}", skuId, ex);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "上传 MinIO 失败: " + ex.getMessage());
        }
    }

    private String buildPublicUrl(String objectKey) {
        return publicEndpoint.endsWith("/")
                ? publicEndpoint + bucket + "/" + objectKey
                : publicEndpoint + "/" + bucket + "/" + objectKey;
    }

    private String sanitize(String original) {
        if (original == null || original.isBlank()) {
            return "file.bin";
        }
        return original.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
