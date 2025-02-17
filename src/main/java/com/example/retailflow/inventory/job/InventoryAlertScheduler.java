package com.example.retailflow.inventory.job;

import com.example.retailflow.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryAlertScheduler {

    private final InventoryService inventoryService;

    @Scheduled(cron = "${app.inventory.alert-cron:0 0/30 * * * ?}", scheduler = "inventoryTaskScheduler")
    public void scanLowStock() {
        try {
            inventoryService.scanLowStock();
        } catch (Exception ex) {
            log.error("scan low stock failed", ex);
        }
    }
}
