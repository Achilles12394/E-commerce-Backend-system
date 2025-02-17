package com.example.retailflow.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderSummaryResponse {
    private String orderNo;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal payableAmount;
    private LocalDateTime createdAt;
    private LocalDateTime payTime;
}
