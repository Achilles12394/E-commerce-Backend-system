package com.example.retailflow.inventory.service;

import com.example.retailflow.inventory.dto.InventoryAddRequest;
import com.example.retailflow.inventory.dto.InventoryReserveItem;

import java.util.List;

public interface InventoryService {
    Integer getAvailableStock(Long skuId);

    void addStock(InventoryAddRequest request);

    void reserve(String orderNo, List<InventoryReserveItem> items);

    void confirmDeduct(String orderNo);

    void release(String orderNo);

    void scanLowStock();

    void reconcileStockConsistency();
}
