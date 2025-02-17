package com.example.retailflow.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardOverviewResponse {
    private Long todayOrderCount;
    private BigDecimal todayPayAmount;
    private Long toShipCount;
    private Long lowStockCount;
}