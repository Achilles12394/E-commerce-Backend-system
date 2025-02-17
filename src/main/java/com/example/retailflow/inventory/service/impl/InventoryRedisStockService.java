package com.example.retailflow.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import com.example.retailflow.inventory.dto.InventoryReserveItem;
import com.example.retailflow.inventory.entity.InventoryEntity;
import com.example.retailflow.inventory.entity.InventoryReservationEntity;
import com.example.retailflow.inventory.mapper.InventoryMapper;
import com.example.retailflow.inventory.mapper.InventoryReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class InventoryRedisStockService {

    private static final long INIT_LOCK_SECONDS = 5L;

    private final StringRedisTemplate stringRedisTemplate;
    private final InventoryMapper inventoryMapper;
    private final InventoryReservationMapper reservationMapper;

    private final DefaultRedisScript<Long> reserveStockScript = loadScript("lua/reserve_stock.lua");
    private final DefaultRedisScript<Long> rollbackStockScript = loadScript("lua/rollback_stock.lua");

    public Integer getAvailableStock(Long skuId) {
        ensureAvailableStockLoaded(skuId);
        String value = stringRedisTemplate.opsForValue().get(availableStockKey(skuId));
        if (value != null) {
            return Integer.parseInt(value);
        }
        InventoryEntity inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getSkuId, skuId));
        return inventory == null ? 0 : inventory.getAvailableStock();
    }

    public void syncAvailableStock(Long skuId, Integer availableStock) {
        if (skuId == null || availableStock == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(availableStockKey(skuId), String.valueOf(Math.max(availableStock, 0)));
    }

    public void preReserve(String orderNo, List<InventoryReserveItem> items) {
        for (InventoryReserveItem item : items) {
            ensureAvailableStockLoaded(item.getSkuId());
            Long result = stringRedisTemplate.execute(
                    reserveStockScript,
                    List.of(availableStockKey(item.getSkuId()), reservationKey(orderNo)),
                    String.valueOf(item.getQuantity()),
                    String.valueOf(item.getSkuId()));
            if (result == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "redis reserve stock failed");
            }
            if (result == -1L) {
                throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "redis stock cache missing");
            }
            if (result == 0L) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
            }
        }
    }

    public void rollbackReserved(String orderNo, List<InventoryReserveItem> items) {
        for (InventoryReserveItem item : items) {
            rollbackOne(orderNo, item.getSkuId());
        }
    }

    public void releaseReserved(String orderNo, List<InventoryReservationEntity> reservations) {
        for (InventoryReservationEntity reservation : reservations) {
            rollbackOne(orderNo, reservation.getSkuId());
        }
    }

    public void clearReservation(String orderNo, List<InventoryReservationEntity> reservations) {
        for (InventoryReservationEntity reservation : reservations) {
            stringRedisTemplate.opsForHash().delete(reservationKey(orderNo), String.valueOf(reservation.getSkuId()));
        }
        Long size = stringRedisTemplate.opsForHash().size(reservationKey(orderNo));
        if (size != null && size == 0) {
            stringRedisTemplate.delete(reservationKey(orderNo));
        }
    }

    public void validateAvailableStocks(List<Long> skuIds) {
        skuIds.stream().distinct().forEach(this::validateAvailableStock);
    }

    public void validateAvailableStock(Long skuId) {
        if (skuId == null) {
            return;
        }
        InventoryEntity inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getSkuId, skuId));
        int dbAvailable = inventory == null ? 0 : Math.max(inventory.getAvailableStock(), 0);
        String key = availableStockKey(skuId);
        String redisValue = stringRedisTemplate.opsForValue().get(key);
        if (redisValue == null || Integer.parseInt(redisValue) != dbAvailable) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbAvailable));
        }
    }

    public void rebuildReservationSnapshot(String orderNo) {
        String key = reservationKey(orderNo);
        List<InventoryReservationEntity> reservations = reservationMapper.selectList(new LambdaQueryWrapper<InventoryReservationEntity>()
                .eq(InventoryReservationEntity::getOrderNo, orderNo)
                .eq(InventoryReservationEntity::getStatus, "RESERVED"));
        if (reservations.isEmpty()) {
            stringRedisTemplate.delete(key);
            return;
        }

        Map<String, String> snapshot = new LinkedHashMap<>();
        for (InventoryReservationEntity reservation : reservations) {
            snapshot.put(String.valueOf(reservation.getSkuId()), String.valueOf(reservation.getQuantity()));
        }
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForHash().putAll(key, snapshot);
    }

    public void reconcileAllFromDatabase() {
        List<InventoryEntity> inventories = inventoryMapper.selectList(new LambdaQueryWrapper<>());
        for (InventoryEntity inventory : inventories) {
            syncAvailableStock(inventory.getSkuId(), inventory.getAvailableStock());
        }

        List<InventoryReservationEntity> reservations = reservationMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, List<InventoryReservationEntity>> byOrderNo = reservations.stream()
                .collect(java.util.stream.Collectors.groupingBy(InventoryReservationEntity::getOrderNo));
        for (Map.Entry<String, List<InventoryReservationEntity>> entry : byOrderNo.entrySet()) {
            String orderNo = entry.getKey();
            List<InventoryReservationEntity> reservedRows = entry.getValue().stream()
                    .filter(row -> "RESERVED".equals(row.getStatus()))
                    .toList();
            if (reservedRows.isEmpty()) {
                stringRedisTemplate.delete(reservationKey(orderNo));
                continue;
            }

            Map<String, String> snapshot = new LinkedHashMap<>();
            for (InventoryReservationEntity reservation : reservedRows) {
                snapshot.put(String.valueOf(reservation.getSkuId()), String.valueOf(reservation.getQuantity()));
            }
            String key = reservationKey(orderNo);
            stringRedisTemplate.delete(key);
            stringRedisTemplate.opsForHash().putAll(key, snapshot);
        }
    }

    private void rollbackOne(String orderNo, Long skuId) {
        ensureAvailableStockLoaded(skuId);
        stringRedisTemplate.execute(
                rollbackStockScript,
                List.of(availableStockKey(skuId), reservationKey(orderNo)),
                String.valueOf(skuId));
    }

    private void ensureAvailableStockLoaded(Long skuId) {
        String key = availableStockKey(skuId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }

        String lockKey = stockInitLockKey(skuId);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, INIT_LOCK_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            for (int i = 0; i < 20; i++) {
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                    return;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            return;
        }

        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            InventoryEntity inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<InventoryEntity>()
                    .eq(InventoryEntity::getSkuId, skuId));
            int availableStock = inventory == null ? 0 : inventory.getAvailableStock();
            stringRedisTemplate.opsForValue().set(key, String.valueOf(Math.max(availableStock, 0)));
        } finally {
            String currentLockValue = stringRedisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentLockValue)) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }

    private String availableStockKey(Long skuId) {
        return "stock:available:" + skuId;
    }

    private String reservationKey(String orderNo) {
        return "stock:reservation:" + orderNo;
    }

    private String stockInitLockKey(Long skuId) {
        return "stock:init:lock:" + skuId;
    }

    private DefaultRedisScript<Long> loadScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}
