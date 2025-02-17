package com.example.retailflow.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import com.example.retailflow.inventory.dto.InventoryAddRequest;
import com.example.retailflow.inventory.dto.InventoryReserveItem;
import com.example.retailflow.inventory.entity.InventoryEntity;
import com.example.retailflow.inventory.entity.InventoryLogEntity;
import com.example.retailflow.inventory.entity.InventoryReservationEntity;
import com.example.retailflow.inventory.entity.StockAlertRecordEntity;
import com.example.retailflow.inventory.mapper.*;
import com.example.retailflow.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final int ALERT_THRESHOLD = 10;

    private final InventoryMapper inventoryMapper;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final StockAlertRecordMapper stockAlertRecordMapper;
    private final InventoryRedisStockService inventoryRedisStockService;

    @Override
    public Integer getAvailableStock(Long skuId) {
        return inventoryRedisStockService.getAvailableStock(skuId);
    }

    @Override
    @Transactional
    public void addStock(InventoryAddRequest request) {
        InventoryEntity inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getSkuId, request.getSkuId()));
        if (inventory == null) {
            inventory = new InventoryEntity();
            inventory.setId(System.nanoTime());
            inventory.setSkuId(request.getSkuId());
            inventory.setTotalStock(request.getQuantity());
            inventory.setAvailableStock(request.getQuantity());
            inventory.setFrozenStock(0);
            inventory.setVersion(0);
            inventoryMapper.insert(inventory);
            Integer availableStock = inventory.getAvailableStock();
            runAfterCommit(() -> inventoryRedisStockService.syncAvailableStock(request.getSkuId(), availableStock));
            return;
        }
        inventory.setTotalStock(inventory.getTotalStock() + request.getQuantity());
        inventory.setAvailableStock(inventory.getAvailableStock() + request.getQuantity());
        inventoryMapper.updateById(inventory);
        Integer availableStock = inventory.getAvailableStock();
        runAfterCommit(() -> inventoryRedisStockService.syncAvailableStock(request.getSkuId(), availableStock));
    }

    @Override
    @Transactional
    public void reserve(String orderNo, List<InventoryReserveItem> items) {
        List<InventoryReserveItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(InventoryReserveItem::getSkuId))
                .toList();
        inventoryRedisStockService.preReserve(orderNo, sortedItems);
        try {
            for (InventoryReserveItem item : sortedItems) {
                int updated = inventoryMapper.reserve(item.getSkuId(), item.getQuantity());
                if (updated == 0) {
                    throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
                }
                InventoryReservationEntity reservation = new InventoryReservationEntity();
                reservation.setId(System.nanoTime());
                reservation.setOrderNo(orderNo);
                reservation.setSkuId(item.getSkuId());
                reservation.setQuantity(item.getQuantity());
                reservation.setStatus("RESERVED");
                reservationMapper.insert(reservation);
                log(item.getSkuId(), "RESERVE", orderNo, -item.getQuantity());
            }
            List<Long> touchedSkuIds = sortedItems.stream()
                    .map(InventoryReserveItem::getSkuId)
                    .toList();
            runAfterCommit(() -> {
                inventoryRedisStockService.validateAvailableStocks(touchedSkuIds);
                inventoryRedisStockService.rebuildReservationSnapshot(orderNo);
            });
        } catch (RuntimeException ex) {
            inventoryRedisStockService.rollbackReserved(orderNo, sortedItems);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void confirmDeduct(String orderNo) {
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<InventoryReservationEntity>()
                        .eq(InventoryReservationEntity::getOrderNo, orderNo)
                        .eq(InventoryReservationEntity::getStatus, "RESERVED"));

        List<InventoryReservationEntity> deductedReservations = new java.util.ArrayList<>();
        for (InventoryReservationEntity reservation : reservations) {
            int updated = inventoryMapper.confirmDeduct(reservation.getSkuId(), reservation.getQuantity());
            if (updated > 0) {
                reservation.setStatus("DEDUCTED");
                reservationMapper.updateById(reservation);
                log(reservation.getSkuId(), "DEDUCT", orderNo, -reservation.getQuantity());
                deductedReservations.add(reservation);
            }
        }
        List<Long> touchedSkuIds = deductedReservations.stream()
                .map(InventoryReservationEntity::getSkuId)
                .toList();
        runAfterCommit(() -> {
            inventoryRedisStockService.clearReservation(orderNo, deductedReservations);
            inventoryRedisStockService.validateAvailableStocks(touchedSkuIds);
        });
    }

    @Override
    @Transactional
    public void release(String orderNo) {
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<InventoryReservationEntity>()
                        .eq(InventoryReservationEntity::getOrderNo, orderNo)
                        .eq(InventoryReservationEntity::getStatus, "RESERVED"));

        List<InventoryReservationEntity> releasedReservations = new java.util.ArrayList<>();
        for (InventoryReservationEntity reservation : reservations) {
            int updated = inventoryMapper.release(reservation.getSkuId(), reservation.getQuantity());
            if (updated > 0) {
                reservation.setStatus("RELEASED");
                reservationMapper.updateById(reservation);
                log(reservation.getSkuId(), "RELEASE", orderNo, reservation.getQuantity());
                releasedReservations.add(reservation);
            }
        }
        List<Long> touchedSkuIds = releasedReservations.stream()
                .map(InventoryReservationEntity::getSkuId)
                .toList();
        runAfterCommit(() -> {
            inventoryRedisStockService.releaseReserved(orderNo, releasedReservations);
            inventoryRedisStockService.validateAvailableStocks(touchedSkuIds);
        });
    }

    @Override
    public void scanLowStock() {
        List<InventoryEntity> lowStock = inventoryMapper.selectList(
                new LambdaQueryWrapper<InventoryEntity>().lt(InventoryEntity::getAvailableStock, ALERT_THRESHOLD));
        for (InventoryEntity inventory : lowStock) {
            StockAlertRecordEntity latestRecord = stockAlertRecordMapper.selectOne(new LambdaQueryWrapper<StockAlertRecordEntity>()
                    .eq(StockAlertRecordEntity::getSkuId, inventory.getSkuId())
                    .orderByDesc(StockAlertRecordEntity::getId)
                    .last("limit 1"));
            if (latestRecord != null && latestRecord.getAvailableStock().equals(inventory.getAvailableStock())) {
                continue;
            }
            StockAlertRecordEntity record = new StockAlertRecordEntity();
            record.setId(System.nanoTime());
            record.setSkuId(inventory.getSkuId());
            record.setAvailableStock(inventory.getAvailableStock());
            record.setThresholdStock(ALERT_THRESHOLD);
            stockAlertRecordMapper.insert(record);
        }
    }

    @Override
    public void reconcileStockConsistency() {
        inventoryRedisStockService.reconcileAllFromDatabase();
    }

    private void log(Long skuId, String bizType, String bizNo, Integer change) {
        InventoryLogEntity log = new InventoryLogEntity();
        log.setId(System.nanoTime());
        log.setSkuId(skuId);
        log.setBizType(bizType);
        log.setBizNo(bizNo);
        log.setChangeAmount(change);
        inventoryLogMapper.insert(log);
    }

    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
