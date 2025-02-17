package com.example.retailflow.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.admin.dto.AdminOrderQueryRequest;
import com.example.retailflow.admin.dto.AdminOrderSummaryResponse;
import com.example.retailflow.admin.dto.AdminShipOrderRequest;
import com.example.retailflow.order.dto.OrderDetailResponse;

public interface AdminOrderService {
    Page<AdminOrderSummaryResponse> page(AdminOrderQueryRequest request);

    OrderDetailResponse detail(String orderNo);

    void ship(String orderNo, Long operatorId, AdminShipOrderRequest request);
}
