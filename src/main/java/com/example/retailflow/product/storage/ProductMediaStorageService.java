package com.example.retailflow.product.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ProductMediaStorageService {
    StoredFileResult storeSkuImage(Long skuId, MultipartFile file);
}
