package com.example.retailflow.product.storage;

import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalProductMediaStorageService implements ProductMediaStorageService {

    @Value("${app.storage.local-dir:uploads}")
    private String localDir;

    @Override
    public StoredFileResult storeSkuImage(Long skuId, MultipartFile file) {
        try {
            String fileName = System.currentTimeMillis() + "-" + sanitize(file.getOriginalFilename());
            Path saveDir = Paths.get(localDir, "sku", String.valueOf(skuId));
            Files.createDirectories(saveDir);
            Path target = saveDir.resolve(fileName);
            file.transferTo(target.toFile());

            return StoredFileResult.builder()
                    .storagePlatform("LOCAL")
                    .objectKey(target.toString())
                    .fileUrl("/uploads/sku/" + skuId + "/" + fileName)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();
        } catch (IOException ex) {
            log.error("store local sku image failed, skuId={}", skuId, ex);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "上传失败: " + ex.getMessage());
        }
    }

    private String sanitize(String original) {
        if (original == null || original.isBlank()) {
            return "file.bin";
        }
        return original.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
