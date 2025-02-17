package com.example.retailflow.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSearchRequest {
    private String keyword;
    private Long categoryId;
    private Long brandId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
