package com.example.retailflow.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.order.dto.*;

import java.util.List;

public interface OrderService {
    void addCartItem(Long userId, CartItemRequest request);

    void updateCartItem(Long userId, Long itemId, Integer quantity);

    void deleteCartItem(Long userId, Long itemId);

    List<CartItemRequest> getCart(Long userId);

    String generateOrderToken(Long userId);

    OrderSubmitResponse submitOrder(Long userId, SubmitOrderRequest request);

    OrderSubmitResponse submitDirectOrder(Long userId, Long skuId, Integer quantity);

    OrderSubmitResponse submitSeckillOrder(Long userId, Long skuId, Integer quantity, java.math.BigDecimal seckillPrice, Long activityId);

    OrderDetailResponse getOrder(String orderNo, Long userId);

    Page<OrderSummaryResponse> page(Long userId, Integer pageNum, Integer pageSize);

    void pay(String orderNo, Long userId);

    void cancel(String orderNo, Long userId, String reason);

    void confirmReceipt(String orderNo, Long userId);

    void closeTimeoutOrders();
}
