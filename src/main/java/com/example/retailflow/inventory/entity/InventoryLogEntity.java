package com.example.retailflow.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("inventory_log")
public class InventoryLogEntity {
    private Long id;
    private Long skuId;
    private String bizType;
    private String bizNo;
    private Integer changeAmount;
    private Integer beforeAvailable;
    private Integer afterAvailable;
}