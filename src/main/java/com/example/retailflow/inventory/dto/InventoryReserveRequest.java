package com.example.retailflow.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InventoryReserveRequest {
    @NotBlank
    private String orderNo;
    @NotEmpty
    private List<InventoryReserveItem> items;
}