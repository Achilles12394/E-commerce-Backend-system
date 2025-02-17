package com.example.retailflow.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.product.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    Long createCategory(CreateCategoryRequest request);

    Long createBrand(CreateBrandRequest request);

    Long createProduct(CreateProductRequest request);

    Long updateProduct(Long skuId, CreateProductRequest request);

    void publish(Long skuId);

    void unpublish(Long skuId);

    String uploadSkuImage(Long skuId, MultipartFile file);

    ProductResponse getProduct(Long skuId);

    Page<ProductResponse> page(Integer pageNum, Integer pageSize);

    List<ProductResponse> search(ProductSearchRequest request);

    long rebuildSearchIndex();

    List<CreateCategoryRequest> categoryCacheList();
}
