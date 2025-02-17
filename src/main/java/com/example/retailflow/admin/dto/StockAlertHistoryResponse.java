package com.example.retailflow.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StockAlertHistoryResponse {
    private Long skuId;
    private Integer availableStock;
    private Integer thresholdStock;
    private LocalDateTime alertTime;
}
