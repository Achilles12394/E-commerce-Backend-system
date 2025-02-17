package com.example.retailflow.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InventoryReserveItem {
    private Long skuId;
    private Integer quantity;
}