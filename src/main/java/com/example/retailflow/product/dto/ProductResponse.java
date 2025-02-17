package com.example.retailflow.product.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductResponse {
    private Long skuId;
    private String skuCode;
    private Long spuId;
    private String title;
    private String subtitle;
    private BigDecimal price;
    private Integer publishStatus;
    private String categoryName;
    private String brandName;
}