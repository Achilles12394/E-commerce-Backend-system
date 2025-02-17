package com.example.retailflow.seckill.service.impl;

import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import com.example.retailflow.product.dto.ProductResponse;
import com.example.retailflow.seckill.entity.SeckillActivityEntity;
import com.example.retailflow.seckill.entity.SeckillActivitySkuEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SeckillRedisService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.seckill.submit-lock-seconds:3}")
    private long submitLockSeconds;

    @Value("${app.seckill.user-rate-limit.window-seconds:1}")
    private long userRateLimitWindowSeconds;

    @Value("${app.seckill.user-rate-limit.max-requests:3}")
    private long userRateLimitMaxRequests;

    private final DefaultRedisScript<Long> reserveScript = loadScript("lua/seckill_reserve.lua");
    private final DefaultRedisScript<Long> rollbackScript = loadScript("lua/seckill_rollback.lua");

    public void preheat(SeckillActivityEntity activity, SeckillActivitySkuEntity item, ProductResponse product, Integer availableStock) {
        int actualStock = Math.max(Math.min(item.getSeckillStock(), availableStock == null ? 0 : availableStock), 0);
        long ttlSeconds = ttlSeconds(activity);

        String activityKey = activityKey(activity.getId());
        stringRedisTemplate.opsForHash().putAll(activityKey, Map.of(
                "activityName", activity.getActivityName(),
                "status", String.valueOf(activity.getStatus()),
                "startAt", String.valueOf(toEpochMillis(activity.getStartTime())),
                "endAt", String.valueOf(toEpochMillis(activity.getEndTime()))
        ));
        stringRedisTemplate.expire(activityKey, ttlSeconds, TimeUnit.SECONDS);

        String skuKey = skuKey(activity.getId(), item.getSkuId());
        stringRedisTemplate.opsForHash().putAll(skuKey, Map.of(
                "skuId", String.valueOf(item.getSkuId()),
                "skuTitle", product.getTitle(),
                "originPrice", product.getPrice().toPlainString(),
                "seckillPrice", item.getSeckillPrice().toPlainString(),
                "limitPerUser", String.valueOf(item.getLimitPerUser()),
                "seckillStock", String.valueOf(actualStock)
        ));
        stringRedisTemplate.expire(skuKey, ttlSeconds, TimeUnit.SECONDS);

        String stockKey = stockKey(activity.getId(), item.getSkuId());
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(actualStock), ttlSeconds, TimeUnit.SECONDS);
    }

    public void reconcileSnapshot(SeckillActivityEntity activity,
                                  SeckillActivitySkuEntity item,
                                  ProductResponse product,
                                  Integer remainingStock,
                                  Map<Long, Integer> expectedUserBuyCounts) {
        int actualStock = Math.max(remainingStock == null ? 0 : remainingStock, 0);
        long ttlSeconds = ttlSeconds(activity);

        preheat(activity, item, product, actualStock);

        Set<String> expectedKeys = new HashSet<>();
        for (Map.Entry<Long, Integer> entry : expectedUserBuyCounts.entrySet()) {
            Integer quantity = entry.getValue();
            if (quantity == null || quantity <= 0) {
                continue;
            }
            String key = userBuyKey(activity.getId(), item.getSkuId(), entry.getKey());
            expectedKeys.add(key);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(quantity), ttlSeconds, TimeUnit.SECONDS);
        }

        Set<String> existingKeys = stringRedisTemplate.keys(userBuyPattern(activity.getId(), item.getSkuId()));
        if (existingKeys != null) {
            for (String existingKey : existingKeys) {
                if (!expectedKeys.contains(existingKey)) {
                    stringRedisTemplate.delete(existingKey);
                }
            }
        }
    }

    public void reserve(Long activityId, Long skuId, Long userId, Integer quantity, Integer limitPerUser) {
        Long result = stringRedisTemplate.execute(
                reserveScript,
                List.of(activityKey(activityId), stockKey(activityId, skuId), userBuyKey(activityId, skuId, userId)),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(quantity),
                String.valueOf(limitPerUser));

        if (result == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "seckill reserve failed");
        }
        if (result == -1L) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "秒杀活动未预热或不存在");
        }
        if (result == -2L) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "秒杀活动未开始或已结束");
        }
        if (result == -3L) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "超过限购数量");
        }
        if (result == 0L) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        refreshUserBuyKeyTtl(activityId, skuId, userId);
    }

    public boolean acquireSubmitLock(Long activityId, Long skuId, Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                submitLockKey(activityId, skuId, userId),
                "1",
                submitLockSeconds,
                TimeUnit.SECONDS));
    }

    public boolean allowUserRequest(Long activityId, Long skuId, Long userId) {
        String key = userRateLimitKey(activityId, skuId, userId);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            return true;
        }
        if (count == 1L) {
            stringRedisTemplate.expire(key, userRateLimitWindowSeconds, TimeUnit.SECONDS);
        }
        return count <= userRateLimitMaxRequests;
    }

    public void rollback(Long activityId, Long skuId, Long userId, Integer quantity) {
        stringRedisTemplate.execute(
                rollbackScript,
                List.of(stockKey(activityId, skuId), userBuyKey(activityId, skuId, userId)),
                String.valueOf(quantity));
    }

    public Integer getStock(Long activityId, Long skuId) {
        String stock = stringRedisTemplate.opsForValue().get(stockKey(activityId, skuId));
        return stock == null ? null : Integer.parseInt(stock);
    }

    private long toEpochMillis(java.time.LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String activityKey(Long activityId) {
        return "seckill:activity:" + activityId;
    }

    private String skuKey(Long activityId, Long skuId) {
        return "seckill:sku:" + activityId + ":" + skuId;
    }

    private String stockKey(Long activityId, Long skuId) {
        return "seckill:stock:" + activityId + ":" + skuId;
    }

    private String userBuyKey(Long activityId, Long skuId, Long userId) {
        return "seckill:user:buy:" + activityId + ":" + skuId + ":" + userId;
    }

    private String userBuyPattern(Long activityId, Long skuId) {
        return "seckill:user:buy:" + activityId + ":" + skuId + ":*";
    }

    private String submitLockKey(Long activityId, Long skuId, Long userId) {
        return "seckill:submit:lock:" + activityId + ":" + skuId + ":" + userId;
    }

    private String userRateLimitKey(Long activityId, Long skuId, Long userId) {
        return "seckill:rate:user:" + activityId + ":" + skuId + ":" + userId;
    }

    private void refreshUserBuyKeyTtl(Long activityId, Long skuId, Long userId) {
        String endAtValue = (String) stringRedisTemplate.opsForHash().get(activityKey(activityId), "endAt");
        if (endAtValue == null) {
            return;
        }
        long endAt = Long.parseLong(endAtValue);
        long ttlMillis = endAt + TimeUnit.MINUTES.toMillis(30) - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            ttlMillis = TimeUnit.MINUTES.toMillis(30);
        }
        stringRedisTemplate.expire(userBuyKey(activityId, skuId, userId), ttlMillis, TimeUnit.MILLISECONDS);
    }

    private long ttlSeconds(SeckillActivityEntity activity) {
        return Math.max(60, java.time.Duration.between(java.time.LocalDateTime.now(), activity.getEndTime().plusMinutes(30)).getSeconds());
    }

    private DefaultRedisScript<Long> loadScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}
