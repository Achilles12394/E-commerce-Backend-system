package com.example.retailflow.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.admin.dto.AdminOrderQueryRequest;
import com.example.retailflow.admin.dto.AdminOrderSummaryResponse;
import com.example.retailflow.admin.dto.AdminShipOrderRequest;
import com.example.retailflow.admin.service.AdminOrderService;
import com.example.retailflow.common.api.ApiResponse;
import com.example.retailflow.common.context.SecurityUtils;
import com.example.retailflow.order.dto.OrderDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping("/page")
    public ApiResponse<Page<AdminOrderSummaryResponse>> page(AdminOrderQueryRequest request) {
        return ApiResponse.success(adminOrderService.page(request));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailResponse> detail(@PathVariable String orderNo) {
        return ApiResponse.success(adminOrderService.detail(orderNo));
    }

    @PostMapping("/{orderNo}/ship")
    public ApiResponse<Void> ship(@PathVariable String orderNo, @Valid @RequestBody AdminShipOrderRequest request) {
        adminOrderService.ship(orderNo, SecurityUtils.currentUserId(), request);
        return ApiResponse.success();
    }
}
