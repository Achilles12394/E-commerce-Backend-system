package com.example.retailflow.admin.dto;

import lombok.Data;

@Data
public class HotProductResponse {
    private Long skuId;
    private String skuTitle;
    private Long soldQuantity;
}