package com.example.retailflow.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import com.example.retailflow.inventory.entity.InventoryEntity;
import com.example.retailflow.inventory.mapper.InventoryMapper;
import com.example.retailflow.inventory.service.InventoryService;
import com.example.retailflow.order.dto.OrderSubmitResponse;
import com.example.retailflow.order.service.OrderService;
import com.example.retailflow.product.dto.ProductResponse;
import com.example.retailflow.product.service.ProductService;
import com.example.retailflow.seckill.dto.CreateSeckillActivityRequest;
import com.example.retailflow.seckill.dto.CreateSeckillActivitySkuRequest;
import com.example.retailflow.seckill.dto.SeckillActivityItemResponse;
import com.example.retailflow.seckill.dto.SeckillActivityResponse;
import com.example.retailflow.seckill.entity.SeckillActivityEntity;
import com.example.retailflow.seckill.entity.SeckillActivitySkuEntity;
import com.example.retailflow.seckill.entity.SeckillOrderEntity;
import com.example.retailflow.seckill.mapper.SeckillActivityMapper;
import com.example.retailflow.seckill.mapper.SeckillActivitySkuMapper;
import com.example.retailflow.seckill.mapper.SeckillOrderMapper;
import com.example.retailflow.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_PUBLISHED = 1;

    private final SeckillActivityMapper activityMapper;
    private final SeckillActivitySkuMapper activitySkuMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final OrderService orderService;
    private final SeckillRedisService seckillRedisService;

    @Override
    @Transactional
    public Long createActivity(CreateSeckillActivityRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "结束时间必须晚于开始时间");
        }
        SeckillActivityEntity entity = new SeckillActivityEntity();
        entity.setId(System.nanoTime());
        entity.setActivityName(request.getActivityName());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setStatus(STATUS_DRAFT);
        activityMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public Long addActivitySku(Long activityId, CreateSeckillActivitySkuRequest request) {
        SeckillActivityEntity activity = requireActivity(activityId);
        ProductResponse product = productService.getProduct(request.getSkuId());
        if (!Integer.valueOf(1).equals(product.getPublishStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "只能添加已上架商品到秒杀活动");
        }
        if (request.getSeckillPrice().compareTo(product.getPrice()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "秒杀价不能高于商品原价");
        }
        int availableStock = inventoryService.getAvailableStock(request.getSkuId());
        if (request.getSeckillStock() > availableStock) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "秒杀库存不能超过当前可用库存");
        }
        SeckillActivitySkuEntity existing = activitySkuMapper.selectOne(new LambdaQueryWrapper<SeckillActivitySkuEntity>()
                .eq(SeckillActivitySkuEntity::getActivityId, activityId)
                .eq(SeckillActivitySkuEntity::getSkuId, request.getSkuId()));
        if (existing != null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "该商品已在当前秒杀活动中");
        }
        SeckillActivitySkuEntity entity = new SeckillActivitySkuEntity();
        entity.setId(System.nanoTime());
        entity.setActivityId(activity.getId());
        entity.setSkuId(request.getSkuId());
        entity.setSeckillPrice(request.getSeckillPrice());
        entity.setSeckillStock(request.getSeckillStock());
        entity.setLimitPerUser(request.getLimitPerUser());
        entity.setStatus(STATUS_PUBLISHED);
        activitySkuMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public void publishActivity(Long activityId) {
        SeckillActivityEntity activity = requireActivity(activityId);
        if (!activity.getEndTime().isAfter(activity.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "活动时间配置不合法");
        }
        long itemCount = activitySkuMapper.selectCount(new LambdaQueryWrapper<SeckillActivitySkuEntity>()
                .eq(SeckillActivitySkuEntity::getActivityId, activityId)
                .eq(SeckillActivitySkuEntity::getStatus, STATUS_PUBLISHED));
        if (itemCount == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "秒杀活动至少需要一个商品");
        }
        activity.setStatus(STATUS_PUBLISHED);
        activityMapper.updateById(activity);
    }

    @Override
    public List<SeckillActivityResponse> listAvailableActivities() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivityEntity> activities = activityMapper.selectList(new LambdaQueryWrapper<SeckillActivityEntity>()
                .eq(SeckillActivityEntity::getStatus, STATUS_PUBLISHED)
                .le(SeckillActivityEntity::getStartTime, now.plusMinutes(30))
                .ge(SeckillActivityEntity::getEndTime, now.minusMinutes(30))
                .orderByAsc(SeckillActivityEntity::getStartTime));
        return buildResponses(activities);
    }

    @Override
    public void preheatUpcomingActivities() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime preheatDeadline = now.plusMinutes(5);
        List<SeckillActivityEntity> activities = activityMapper.selectList(new LambdaQueryWrapper<SeckillActivityEntity>()
                .eq(SeckillActivityEntity::getStatus, STATUS_PUBLISHED)
                .le(SeckillActivityEntity::getStartTime, preheatDeadline)
                .ge(SeckillActivityEntity::getEndTime, now));
        for (SeckillActivityEntity activity : activities) {
            List<SeckillActivitySkuEntity> items = activitySkuMapper.selectList(new LambdaQueryWrapper<SeckillActivitySkuEntity>()
                    .eq(SeckillActivitySkuEntity::getActivityId, activity.getId())
                    .eq(SeckillActivitySkuEntity::getStatus, STATUS_PUBLISHED));
            for (SeckillActivitySkuEntity item : items) {
                ProductResponse product = productService.getProduct(item.getSkuId());
                Integer availableStock = inventoryService.getAvailableStock(item.getSkuId());
                seckillRedisService.preheat(activity, item, product, availableStock);
            }
        }
    }

    @Override
    public void reconcileRecentActivities() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivityEntity> activities = activityMapper.selectList(new LambdaQueryWrapper<SeckillActivityEntity>()
                .eq(SeckillActivityEntity::getStatus, STATUS_PUBLISHED)
                .le(SeckillActivityEntity::getStartTime, now.plusMinutes(30))
                .ge(SeckillActivityEntity::getEndTime, now.minusMinutes(30)));

        for (SeckillActivityEntity activity : activities) {
            List<SeckillActivitySkuEntity> items = activitySkuMapper.selectList(new LambdaQueryWrapper<SeckillActivitySkuEntity>()
                    .eq(SeckillActivitySkuEntity::getActivityId, activity.getId())
                    .eq(SeckillActivitySkuEntity::getStatus, STATUS_PUBLISHED));
            if (items.isEmpty()) {
                continue;
            }

            Map<Long, ProductResponse> productMap = items.stream()
                    .map(SeckillActivitySkuEntity::getSkuId)
                    .distinct()
                    .map(productService::getProduct)
                    .collect(Collectors.toMap(ProductResponse::getSkuId, Function.identity()));

            List<SeckillOrderEntity> effectiveOrders = seckillOrderMapper.selectList(new LambdaQueryWrapper<SeckillOrderEntity>()
                    .eq(SeckillOrderEntity::getActivityId, activity.getId())
                    .in(SeckillOrderEntity::getStatus, List.of("CREATED", "PAID")));

            Map<Long, Integer> lockedBySku = effectiveOrders.stream()
                    .collect(Collectors.groupingBy(SeckillOrderEntity::getSkuId,
                            Collectors.summingInt(SeckillOrderEntity::getQuantity)));

            Map<Long, Map<Long, Integer>> buyCountBySkuUser = new LinkedHashMap<>();
            for (SeckillOrderEntity order : effectiveOrders) {
                buyCountBySkuUser
                        .computeIfAbsent(order.getSkuId(), key -> new LinkedHashMap<>())
                        .merge(order.getUserId(), order.getQuantity(), Integer::sum);
            }

            for (SeckillActivitySkuEntity item : items) {
                ProductResponse product = productMap.get(item.getSkuId());
                InventoryEntity inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<InventoryEntity>()
                        .eq(InventoryEntity::getSkuId, item.getSkuId()));
                int availableStock = inventory == null ? 0 : Math.max(inventory.getAvailableStock(), 0);
                int consumed = lockedBySku.getOrDefault(item.getSkuId(), 0);
                int remainingStock = Math.max(Math.min(item.getSeckillStock() - consumed, availableStock), 0);
                Map<Long, Integer> expectedUserBuyCounts = buyCountBySkuUser.getOrDefault(item.getSkuId(), Map.of());
                seckillRedisService.reconcileSnapshot(activity, item, product, remainingStock, expectedUserBuyCounts);
            }
        }
    }

    @Override
    @Transactional
    public OrderSubmitResponse submit(Long userId, Long activityId, Long skuId, Integer quantity) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!seckillRedisService.allowUserRequest(activityId, skuId, userId)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "too many seckill requests, please try again later");
        }
        if (!seckillRedisService.acquireSubmitLock(activityId, skuId, userId)) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT.getCode(), "request is being processed, please do not submit repeatedly");
        }
        SeckillActivityEntity activity = requireActivity(activityId);
        if (!Integer.valueOf(STATUS_PUBLISHED).equals(activity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "秒杀活动未发布");
        }
        SeckillActivitySkuEntity item = activitySkuMapper.selectOne(new LambdaQueryWrapper<SeckillActivitySkuEntity>()
                .eq(SeckillActivitySkuEntity::getActivityId, activityId)
                .eq(SeckillActivitySkuEntity::getSkuId, skuId)
                .eq(SeckillActivitySkuEntity::getStatus, STATUS_PUBLISHED));
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "秒杀商品不存在");
        }

        seckillRedisService.reserve(activityId, skuId, userId, quantity, item.getLimitPerUser());
        try {
            OrderSubmitResponse response = orderService.submitSeckillOrder(userId, skuId, quantity, item.getSeckillPrice(), activityId);
            SeckillOrderEntity order = new SeckillOrderEntity();
            order.setId(System.nanoTime());
            order.setOrderNo(response.getOrderNo());
            order.setActivityId(activityId);
            order.setSkuId(skuId);
            order.setUserId(userId);
            order.setQuantity(quantity);
            order.setSeckillPrice(item.getSeckillPrice());
            order.setStatus("CREATED");
            seckillOrderMapper.insert(order);
            return response;
        } catch (RuntimeException ex) {
            seckillRedisService.rollback(activityId, skuId, userId, quantity);
            throw ex;
        }
    }

    private SeckillActivityEntity requireActivity(Long activityId) {
        SeckillActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "秒杀活动不存在");
        }
        return activity;
    }

    private List<SeckillActivityResponse> buildResponses(List<SeckillActivityEntity> activities) {
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> activityIds = activities.stream().map(SeckillActivityEntity::getId).toList();
        List<SeckillActivitySkuEntity> allItems = activitySkuMapper.selectList(new LambdaQueryWrapper<SeckillActivitySkuEntity>()
                .in(SeckillActivitySkuEntity::getActivityId, activityIds)
                .eq(SeckillActivitySkuEntity::getStatus, STATUS_PUBLISHED));
        Map<Long, ProductResponse> productMap = allItems.stream()
                .map(SeckillActivitySkuEntity::getSkuId)
                .distinct()
                .map(productService::getProduct)
                .collect(Collectors.toMap(ProductResponse::getSkuId, Function.identity()));
        Map<Long, List<SeckillActivityItemResponse>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(SeckillActivitySkuEntity::getActivityId,
                        Collectors.mapping(item -> {
                            ProductResponse product = productMap.get(item.getSkuId());
                            Integer redisStock = seckillRedisService.getStock(item.getActivityId(), item.getSkuId());
                            return SeckillActivityItemResponse.builder()
                                    .skuId(item.getSkuId())
                                    .skuTitle(product == null ? null : product.getTitle())
                                    .originPrice(product == null ? item.getSeckillPrice() : product.getPrice())
                                    .seckillPrice(item.getSeckillPrice())
                                    .seckillStock(redisStock == null ? item.getSeckillStock() : redisStock)
                                    .limitPerUser(item.getLimitPerUser())
                                    .status(item.getStatus())
                                    .build();
                        }, Collectors.toList())));

        return activities.stream()
                .map(activity -> SeckillActivityResponse.builder()
                        .activityId(activity.getId())
                        .activityName(activity.getActivityName())
                        .startTime(activity.getStartTime())
                        .endTime(activity.getEndTime())
                        .status(activity.getStatus())
                        .items(itemMap.getOrDefault(activity.getId(), List.of()))
                        .build())
                .toList();
    }
}
