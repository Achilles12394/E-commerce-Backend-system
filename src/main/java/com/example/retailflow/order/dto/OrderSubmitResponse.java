package com.example.retailflow.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderSubmitResponse {
    private String orderNo;
    private BigDecimal payableAmount;
}