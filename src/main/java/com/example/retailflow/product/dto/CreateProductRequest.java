package com.example.retailflow.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {
    @NotBlank
    private String spuCode;
    @NotBlank
    private String skuCode;
    @NotBlank
    private String title;
    private String subtitle;
    @NotNull
    private Long categoryId;
    @NotNull
    private Long brandId;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;
}