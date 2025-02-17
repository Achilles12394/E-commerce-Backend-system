package com.example.retailflow.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("inventory")
public class InventoryEntity {
    private Long id;
    private Long skuId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer frozenStock;
    private Integer version;
    private Integer deleted;
}