package com.example.retailflow.product.search;

import com.example.retailflow.product.dto.ProductResponse;
import com.example.retailflow.product.dto.ProductSearchRequest;

import java.util.List;

public interface ProductSearchIndexService {
    void syncProduct(Long skuId);

    void removeProduct(Long skuId);

    List<ProductResponse> search(ProductSearchRequest request);

    long rebuildIndex();
}
