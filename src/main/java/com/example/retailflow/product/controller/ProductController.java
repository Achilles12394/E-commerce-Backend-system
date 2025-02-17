package com.example.retailflow.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.common.api.ApiResponse;
import com.example.retailflow.product.dto.*;
import com.example.retailflow.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "创建分类")
    @PostMapping("/api/admin/categories")
    public ApiResponse<Long> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success(productService.createCategory(request));
    }

    @PostMapping("/api/admin/brands")
    public ApiResponse<Long> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        return ApiResponse.success(productService.createBrand(request));
    }

    @PostMapping("/api/admin/products")
    public ApiResponse<Long> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success(productService.createProduct(request));
    }

    @PutMapping("/api/admin/products/{id}")
    public ApiResponse<Long> updateProduct(@PathVariable Long id, @Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success(productService.updateProduct(id, request));
    }

    @PostMapping("/api/admin/products/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        productService.publish(id);
        return ApiResponse.success();
    }

    @PostMapping("/api/admin/products/{id}/unpublish")
    public ApiResponse<Void> unpublish(@PathVariable Long id) {
        productService.unpublish(id);
        return ApiResponse.success();
    }

    @PostMapping("/api/admin/products/search/rebuild")
    public ApiResponse<Long> rebuildSearchIndex() {
        return ApiResponse.success(productService.rebuildSearchIndex());
    }

    @PostMapping("/api/admin/products/{skuId}/images")
    public ApiResponse<String> upload(@PathVariable Long skuId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(productService.uploadSkuImage(skuId, file));
    }

    @GetMapping("/api/products/{id}")
    public ApiResponse<ProductResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productService.getProduct(id));
    }

    @GetMapping("/api/products/page")
    public ApiResponse<Page<ProductResponse>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(productService.page(pageNum, pageSize));
    }

    @GetMapping("/api/products/search")
    public ApiResponse<List<ProductResponse>> search(ProductSearchRequest request) {
        return ApiResponse.success(productService.search(request));
    }
}
