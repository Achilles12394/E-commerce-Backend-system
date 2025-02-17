package com.example.retailflow.inventory.job;

import com.example.retailflow.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryConsistencyScheduler {

    private final InventoryService inventoryService;

    @Scheduled(cron = "${app.inventory.reconcile-cron:0 */5 * * * ?}", scheduler = "inventoryTaskScheduler")
    public void reconcileStockConsistency() {
        try {
            inventoryService.reconcileStockConsistency();
        } catch (Exception ex) {
            log.error("reconcile stock consistency failed", ex);
        }
    }
}
