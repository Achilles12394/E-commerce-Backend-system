package com.example.retailflow.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.common.api.ApiResponse;
import com.example.retailflow.common.context.SecurityUtils;
import com.example.retailflow.order.dto.*;
import com.example.retailflow.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/cart/items")
    public ApiResponse<Void> addCart(@Valid @RequestBody CartItemRequest request) {
        orderService.addCartItem(SecurityUtils.currentUserId(), request);
        return ApiResponse.success();
    }

    @PutMapping("/api/cart/items/{id}")
    public ApiResponse<Void> updateCart(@PathVariable Long id, @RequestParam Integer quantity) {
        orderService.updateCartItem(SecurityUtils.currentUserId(), id, quantity);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/cart/items/{id}")
    public ApiResponse<Void> deleteCart(@PathVariable Long id) {
        orderService.deleteCartItem(SecurityUtils.currentUserId(), id);
        return ApiResponse.success();
    }

    @GetMapping("/api/cart")
    public ApiResponse<List<CartItemRequest>> cart() {
        return ApiResponse.success(orderService.getCart(SecurityUtils.currentUserId()));
    }

    @GetMapping("/api/orders/confirm")
    public ApiResponse<List<CartItemRequest>> confirm() {
        return ApiResponse.success(orderService.getCart(SecurityUtils.currentUserId()));
    }

    @PostMapping("/api/orders/token")
    public ApiResponse<String> token() {
        return ApiResponse.success(orderService.generateOrderToken(SecurityUtils.currentUserId()));
    }

    @PostMapping("/api/orders/submit")
    public ApiResponse<OrderSubmitResponse> submit(@Valid @RequestBody SubmitOrderRequest request) {
        return ApiResponse.success(orderService.submitOrder(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/api/orders/{orderNo}")
    public ApiResponse<OrderDetailResponse> detail(@PathVariable String orderNo) {
        return ApiResponse.success(orderService.getOrder(orderNo, SecurityUtils.currentUserId()));
    }

    @GetMapping("/api/orders/page")
    public ApiResponse<Page<OrderSummaryResponse>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(orderService.page(SecurityUtils.currentUserId(), pageNum, pageSize));
    }

    @PostMapping("/api/orders/{orderNo}/pay")
    public ApiResponse<Void> pay(@PathVariable String orderNo) {
        orderService.pay(orderNo, SecurityUtils.currentUserId());
        return ApiResponse.success();
    }

    @PostMapping("/api/orders/{orderNo}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String orderNo, @RequestParam(defaultValue = "主动取消") String reason) {
        orderService.cancel(orderNo, SecurityUtils.currentUserId(), reason);
        return ApiResponse.success();
    }

    @PostMapping("/api/orders/{orderNo}/confirm-receipt")
    public ApiResponse<Void> confirmReceipt(@PathVariable String orderNo) {
        orderService.confirmReceipt(orderNo, SecurityUtils.currentUserId());
        return ApiResponse.success();
    }
}
