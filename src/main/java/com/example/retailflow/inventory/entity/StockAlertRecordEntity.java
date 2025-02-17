package com.example.retailflow.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_alert_record")
public class StockAlertRecordEntity {
    private Long id;
    private Long skuId;
    private Integer availableStock;
    private Integer thresholdStock;
    private LocalDateTime createdAt;
}
