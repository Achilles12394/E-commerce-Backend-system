package com.example.retailflow.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryAddRequest {
    @NotNull
    private Long skuId;
    @Min(1)
    private Integer quantity;
}