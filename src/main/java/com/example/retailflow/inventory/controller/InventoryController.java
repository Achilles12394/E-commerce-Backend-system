package com.example.retailflow.inventory.controller;

import com.example.retailflow.common.api.ApiResponse;
import com.example.retailflow.inventory.dto.InventoryAddRequest;
import com.example.retailflow.inventory.dto.InventoryReserveRequest;
import com.example.retailflow.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/api/inventory/{skuId}")
    public ApiResponse<Integer> get(@PathVariable Long skuId) {
        return ApiResponse.success(inventoryService.getAvailableStock(skuId));
    }

    @PostMapping("/api/admin/inventory/add")
    public ApiResponse<Void> add(@Valid @RequestBody InventoryAddRequest request) {
        inventoryService.addStock(request);
        return ApiResponse.success();
    }

    @PostMapping("/api/internal/inventory/reserve")
    public ApiResponse<Void> reserve(@Valid @RequestBody InventoryReserveRequest request) {
        inventoryService.reserve(request.getOrderNo(), request.getItems());
        return ApiResponse.success();
    }

    @PostMapping("/api/internal/inventory/confirm-deduct")
    public ApiResponse<Void> confirmDeduct(@RequestParam String orderNo) {
        inventoryService.confirmDeduct(orderNo);
        return ApiResponse.success();
    }

    @PostMapping("/api/internal/inventory/release")
    public ApiResponse<Void> release(@RequestParam String orderNo) {
        inventoryService.release(orderNo);
        return ApiResponse.success();
    }
}