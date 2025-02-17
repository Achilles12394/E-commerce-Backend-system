package com.example.retailflow.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentRecordResponse {
    private String payChannel;
    private String payStatus;
    private BigDecimal payAmount;
    private String thirdTradeNo;
    private LocalDateTime paidAt;
}
