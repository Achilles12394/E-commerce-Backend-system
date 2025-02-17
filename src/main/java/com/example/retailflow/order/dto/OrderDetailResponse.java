package com.example.retailflow.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderDetailResponse {
    private String orderNo;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal payableAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime payTime;
    private List<OrderItemResponse> items;
    private PaymentRecordResponse payment;
    private List<OrderOperateLogResponse> operateLogs;
}
