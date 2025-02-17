package com.example.retailflow.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("inventory_reservation")
public class InventoryReservationEntity {
    private Long id;
    private String orderNo;
    private Long skuId;
    private Integer quantity;
    private String status;
}