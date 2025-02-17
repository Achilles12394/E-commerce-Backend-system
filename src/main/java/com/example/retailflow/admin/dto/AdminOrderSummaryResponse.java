package com.example.retailflow.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminOrderSummaryResponse {
    private String orderNo;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal payableAmount;
    private LocalDateTime createdAt;
    private LocalDateTime payTime;
}
