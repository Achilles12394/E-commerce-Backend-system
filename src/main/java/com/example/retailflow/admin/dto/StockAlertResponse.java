package com.example.retailflow.admin.dto;

import lombok.Data;

@Data
public class StockAlertResponse {
    private Long skuId;
    private Integer availableStock;
    private Integer thresholdStock;
}