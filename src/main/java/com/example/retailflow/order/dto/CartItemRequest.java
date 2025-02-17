package com.example.retailflow.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemRequest {
    @NotNull
    private Long skuId;
    @NotNull
    private String skuTitle;
    @NotNull
    private BigDecimal price;
    @Min(1)
    private Integer quantity;
}