package com.example.retailflow.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import com.example.retailflow.inventory.dto.InventoryReserveItem;
import com.example.retailflow.inventory.service.InventoryService;
import com.example.retailflow.order.dto.CartItemRequest;
import com.example.retailflow.order.dto.OrderDetailResponse;
import com.example.retailflow.order.dto.OrderItemResponse;
import com.example.retailflow.order.dto.OrderOperateLogResponse;
import com.example.retailflow.order.dto.OrderSubmitResponse;
import com.example.retailflow.order.dto.OrderSummaryResponse;
import com.example.retailflow.order.dto.PaymentRecordResponse;
import com.example.retailflow.order.dto.SubmitOrderRequest;
import com.example.retailflow.order.entity.CartItemEntity;
import com.example.retailflow.order.entity.IdempotentTokenEntity;
import com.example.retailflow.order.entity.OrderItemEntity;
import com.example.retailflow.order.entity.OrderOperateLogEntity;
import com.example.retailflow.order.entity.OrdersEntity;
import com.example.retailflow.order.entity.PaymentRecordEntity;
import com.example.retailflow.order.enums.OrderStatus;
import com.example.retailflow.order.mapper.CartItemMapper;
import com.example.retailflow.order.mapper.IdempotentTokenMapper;
import com.example.retailflow.order.mapper.OrderItemMapper;
import com.example.retailflow.order.mapper.OrderOperateLogMapper;
import com.example.retailflow.order.mapper.OrdersMapper;
import com.example.retailflow.order.mapper.PaymentRecordMapper;
import com.example.retailflow.order.service.OrderService;
import com.example.retailflow.product.dto.ProductResponse;
import com.example.retailflow.product.service.ProductService;
import com.example.retailflow.seckill.service.SeckillOrderSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CartItemMapper cartItemMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final IdempotentTokenMapper idempotentTokenMapper;
    private final OrderOperateLogMapper orderOperateLogMapper;
    private final InventoryService inventoryService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final ObjectProvider<SeckillOrderSyncService> seckillOrderSyncServiceProvider;

    @Value("${app.order.timeout-minutes:30}")
    private Integer orderTimeoutMinutes;

    @Override
    public void addCartItem(Long userId, CartItemRequest request) {
        CartItemEntity entity = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getUserId, userId)
                .eq(CartItemEntity::getSkuId, request.getSkuId()));
        if (entity == null) {
            entity = new CartItemEntity();
            entity.setId(System.nanoTime());
            entity.setUserId(userId);
            entity.setSkuId(request.getSkuId());
            entity.setSkuTitle(request.getSkuTitle());
            entity.setPrice(request.getPrice());
            entity.setQuantity(request.getQuantity());
            entity.setChecked(1);
            cartItemMapper.insert(entity);
        } else {
            entity.setQuantity(entity.getQuantity() + request.getQuantity());
            cartItemMapper.updateById(entity);
        }
        refreshCartCache(userId);
    }

    @Override
    public void updateCartItem(Long userId, Long itemId, Integer quantity) {
        CartItemEntity entity = cartItemMapper.selectById(itemId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        entity.setQuantity(quantity);
        cartItemMapper.updateById(entity);
        refreshCartCache(userId);
    }

    @Override
    public void deleteCartItem(Long userId, Long itemId) {
        CartItemEntity entity = cartItemMapper.selectById(itemId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            return;
        }
        cartItemMapper.deleteById(itemId);
        refreshCartCache(userId);
    }

    @Override
    public List<CartItemRequest> getCart(Long userId) {
        String key = "cart:user:" + userId;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                CartItemRequest[] arr = objectMapper.readValue(cached, CartItemRequest[].class);
                return Arrays.asList(arr);
            } catch (Exception ignored) {
            }
        }
        return refreshCartCache(userId);
    }

    @Override
    public String generateOrderToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = idempotentRedisKey(userId, token);
        stringRedisTemplate.opsForValue().set(redisKey, "1", 15, TimeUnit.MINUTES);

        IdempotentTokenEntity entity = new IdempotentTokenEntity();
        entity.setId(System.nanoTime());
        entity.setToken(token);
        entity.setUserId(userId);
        entity.setBizType("ORDER_SUBMIT");
        entity.setExpiredAt(LocalDateTime.now().plusMinutes(15));
        entity.setUsed(0);
        idempotentTokenMapper.insert(entity);
        return token;
    }

    @Override
    @Transactional
    public OrderSubmitResponse submitOrder(Long userId, SubmitOrderRequest request) {
        String redisKey = idempotentRedisKey(userId, request.getToken());
        Boolean deleted = stringRedisTemplate.delete(redisKey);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT);
        }

        IdempotentTokenEntity token = idempotentTokenMapper.selectOne(new LambdaQueryWrapper<IdempotentTokenEntity>()
                .eq(IdempotentTokenEntity::getToken, request.getToken())
                .eq(IdempotentTokenEntity::getUserId, userId)
                .eq(IdempotentTokenEntity::getUsed, 0));
        if (token == null || token.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT);
        }
        token.setUsed(1);
        idempotentTokenMapper.updateById(token);

        List<CartItemEntity> checkedItems = cartItemMapper.selectList(new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getUserId, userId)
                .eq(CartItemEntity::getChecked, 1));
        return createOrder(userId, checkedItems, true, "USER_SUBMIT");
    }

    @Override
    @Transactional
    public OrderSubmitResponse submitDirectOrder(Long userId, Long skuId, Integer quantity) {
        if (skuId == null || quantity == null || quantity < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid direct order request");
        }

        ProductResponse product = productService.getProduct(skuId);
        if (!Integer.valueOf(1).equals(product.getPublishStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "product is not available");
        }

        CartItemEntity instantItem = new CartItemEntity();
        instantItem.setId(System.nanoTime());
        instantItem.setUserId(userId);
        instantItem.setSkuId(product.getSkuId());
        instantItem.setSkuTitle(product.getTitle());
        instantItem.setPrice(product.getPrice());
        instantItem.setQuantity(quantity);
        instantItem.setChecked(1);
        return createOrder(userId, List.of(instantItem), false, "AGENT_SUBMIT");
    }

    @Override
    @Transactional
    public OrderSubmitResponse submitSeckillOrder(Long userId, Long skuId, Integer quantity, BigDecimal seckillPrice, Long activityId) {
        if (skuId == null || quantity == null || quantity < 1 || seckillPrice == null || activityId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "invalid seckill order request");
        }

        ProductResponse product = productService.getProduct(skuId);
        if (!Integer.valueOf(1).equals(product.getPublishStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "product is not available");
        }

        CartItemEntity instantItem = new CartItemEntity();
        instantItem.setId(System.nanoTime());
        instantItem.setUserId(userId);
        instantItem.setSkuId(product.getSkuId());
        instantItem.setSkuTitle(product.getTitle());
        instantItem.setPrice(seckillPrice);
        instantItem.setQuantity(quantity);
        instantItem.setChecked(1);
        return createOrder(userId, List.of(instantItem), false, "SECKILL_SUBMIT:" + activityId);
    }

    @Override
    public OrderDetailResponse getOrder(String orderNo, Long userId) {
        OrdersEntity order = loadUserOrder(orderNo, userId);
        return toDetail(order);
    }

    @Override
    public Page<OrderSummaryResponse> page(Long userId, Integer pageNum, Integer pageSize) {
        Page<OrdersEntity> page = ordersMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrdersEntity>().eq(OrdersEntity::getUserId, userId).orderByDesc(OrdersEntity::getId));
        Page<OrderSummaryResponse> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toSummary).toList());
        return result;
    }

    @Override
    @Transactional
    public void pay(String orderNo, Long userId) {
        OrdersEntity order = loadUserOrder(orderNo, userId);
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getOrderStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        order.setOrderStatus(OrderStatus.TO_SHIP.name());
        order.setPayTime(LocalDateTime.now());
        ordersMapper.updateById(order);

        PaymentRecordEntity payment = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getOrderNo, orderNo));
        payment.setPayStatus("SUCCESS");
        payment.setPaidAt(LocalDateTime.now());
        payment.setThirdTradeNo("MOCK-" + System.nanoTime());
        paymentRecordMapper.updateById(payment);

        inventoryService.confirmDeduct(orderNo);
        notifySeckillOrderPaid(orderNo);
        recordOperate(orderNo, userId, "PAY", "payment completed");
    }

    @Override
    @Transactional
    public void cancel(String orderNo, Long userId, String reason) {
        OrdersEntity order = loadUserOrder(orderNo, userId);
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getOrderStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        order.setOrderStatus(OrderStatus.CANCELED.name());
        ordersMapper.updateById(order);
        inventoryService.release(orderNo);
        notifySeckillOrderCanceled(orderNo);
        recordOperate(orderNo, userId, "CANCEL", reason);
    }

    @Override
    @Transactional
    public void confirmReceipt(String orderNo, Long userId) {
        OrdersEntity order = loadUserOrder(orderNo, userId);
        if (!OrderStatus.SHIPPED.name().equals(order.getOrderStatus()) && !OrderStatus.TO_SHIP.name().equals(order.getOrderStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        order.setOrderStatus(OrderStatus.COMPLETED.name());
        ordersMapper.updateById(order);
        recordOperate(orderNo, userId, "CONFIRM_RECEIPT", "receipt confirmed");
    }

    @Override
    @Transactional
    public void closeTimeoutOrders() {
        LocalDateTime timeoutPoint = LocalDateTime.now().minusMinutes(orderTimeoutMinutes);
        List<OrdersEntity> timeoutOrders = ordersMapper.selectList(new LambdaQueryWrapper<OrdersEntity>()
                .eq(OrdersEntity::getOrderStatus, OrderStatus.PENDING_PAYMENT.name())
                .lt(OrdersEntity::getCreatedAt, timeoutPoint));

        for (OrdersEntity order : timeoutOrders) {
            order.setOrderStatus(OrderStatus.CLOSED.name());
            ordersMapper.updateById(order);
            inventoryService.release(order.getOrderNo());
            notifySeckillOrderCanceled(order.getOrderNo());
            recordOperate(order.getOrderNo(), order.getUserId(), "TIMEOUT_CLOSE", "timeout closed");
        }
    }

    private OrderSubmitResponse createOrder(Long userId,
                                            List<CartItemEntity> items,
                                            boolean clearCartAfterSubmit,
                                            String createRemark) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "no items selected");
        }

        String orderNo = "ORD" + System.currentTimeMillis();
        List<InventoryReserveItem> reserveItems = items.stream()
                .map(i -> new InventoryReserveItem(i.getSkuId(), i.getQuantity()))
                .toList();
        inventoryService.reserve(orderNo, reserveItems);

        BigDecimal total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrdersEntity order = new OrdersEntity();
        order.setId(System.nanoTime());
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setTotalAmount(total);
        order.setPayableAmount(total);
        ordersMapper.insert(order);

        for (CartItemEntity item : items) {
            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setId(System.nanoTime());
            orderItem.setOrderNo(orderNo);
            orderItem.setSkuId(item.getSkuId());
            orderItem.setSkuTitle(item.getSkuTitle());
            orderItem.setSalePrice(item.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setAmount(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItemMapper.insert(orderItem);
        }

        PaymentRecordEntity payment = new PaymentRecordEntity();
        payment.setId(System.nanoTime());
        payment.setOrderNo(orderNo);
        payment.setPayChannel("MOCK");
        payment.setPayStatus("PENDING");
        payment.setPayAmount(total);
        paymentRecordMapper.insert(payment);

        recordOperate(orderNo, userId, "CREATE", createRemark);

        if (clearCartAfterSubmit) {
            for (CartItemEntity item : items) {
                cartItemMapper.deleteById(item.getId());
            }
            refreshCartCache(userId);
        }

        return OrderSubmitResponse.builder().orderNo(orderNo).payableAmount(total).build();
    }

    private void recordOperate(String orderNo, Long userId, String op, String remark) {
        OrderOperateLogEntity log = new OrderOperateLogEntity();
        log.setId(System.nanoTime());
        log.setOrderNo(orderNo);
        log.setOperatorId(userId);
        log.setOperateType(op);
        log.setRemark(remark);
        orderOperateLogMapper.insert(log);
    }

    private OrdersEntity loadUserOrder(String orderNo, Long userId) {
        OrdersEntity order = ordersMapper.selectOne(new LambdaQueryWrapper<OrdersEntity>()
                .eq(OrdersEntity::getOrderNo, orderNo)
                .eq(OrdersEntity::getUserId, userId));
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    private OrderDetailResponse toDetail(OrdersEntity order) {
        List<OrderItemResponse> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderNo, order.getOrderNo())
                        .orderByAsc(OrderItemEntity::getId))
                .stream()
                .map(item -> OrderItemResponse.builder()
                        .skuId(item.getSkuId())
                        .skuTitle(item.getSkuTitle())
                        .salePrice(item.getSalePrice())
                        .quantity(item.getQuantity())
                        .amount(item.getAmount())
                        .build())
                .toList();

        PaymentRecordEntity payment = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getOrderNo, order.getOrderNo()));

        List<OrderOperateLogResponse> operateLogs = orderOperateLogMapper.selectList(new LambdaQueryWrapper<OrderOperateLogEntity>()
                        .eq(OrderOperateLogEntity::getOrderNo, order.getOrderNo())
                        .orderByDesc(OrderOperateLogEntity::getId))
                .stream()
                .map(log -> OrderOperateLogResponse.builder()
                        .operateType(log.getOperateType())
                        .operatorId(log.getOperatorId())
                        .remark(log.getRemark())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();

        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .status(order.getOrderStatus())
                .totalAmount(order.getTotalAmount())
                .payableAmount(order.getPayableAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .payTime(order.getPayTime())
                .items(items)
                .payment(payment == null ? null : PaymentRecordResponse.builder()
                        .payChannel(payment.getPayChannel())
                        .payStatus(payment.getPayStatus())
                        .payAmount(payment.getPayAmount())
                        .thirdTradeNo(payment.getThirdTradeNo())
                        .paidAt(payment.getPaidAt())
                        .build())
                .operateLogs(operateLogs)
                .build();
    }

    private OrderSummaryResponse toSummary(OrdersEntity order) {
        return OrderSummaryResponse.builder()
                .orderNo(order.getOrderNo())
                .status(order.getOrderStatus())
                .totalAmount(order.getTotalAmount())
                .payableAmount(order.getPayableAmount())
                .createdAt(order.getCreatedAt())
                .payTime(order.getPayTime())
                .build();
    }

    private String idempotentRedisKey(Long userId, String token) {
        return "order:idempotent:" + userId + ":" + token;
    }

    private void notifySeckillOrderPaid(String orderNo) {
        SeckillOrderSyncService syncService = seckillOrderSyncServiceProvider.getIfAvailable();
        if (syncService != null) {
            syncService.handleOrderPaid(orderNo);
        }
    }

    private void notifySeckillOrderCanceled(String orderNo) {
        SeckillOrderSyncService syncService = seckillOrderSyncServiceProvider.getIfAvailable();
        if (syncService != null) {
            syncService.handleOrderCanceled(orderNo);
        }
    }

    private List<CartItemRequest> refreshCartCache(Long userId) {
        List<CartItemRequest> cart = cartItemMapper.selectList(new LambdaQueryWrapper<CartItemEntity>()
                        .eq(CartItemEntity::getUserId, userId))
                .stream().map(item -> {
                    CartItemRequest dto = new CartItemRequest();
                    dto.setSkuId(item.getSkuId());
                    dto.setSkuTitle(item.getSkuTitle());
                    dto.setPrice(item.getPrice());
                    dto.setQuantity(item.getQuantity());
                    return dto;
                }).toList();
        try {
            stringRedisTemplate.opsForValue().set("cart:user:" + userId, objectMapper.writeValueAsString(cart), 1, TimeUnit.DAYS);
        } catch (Exception ignored) {
        }
        return cart;
    }
}
