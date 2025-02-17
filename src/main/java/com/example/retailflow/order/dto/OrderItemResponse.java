package com.example.retailflow.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long skuId;
    private String skuTitle;
    private BigDecimal salePrice;
    private Integer quantity;
    private BigDecimal amount;
}
