package com.example.retailflow.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("order_item")
public class OrderItemEntity {
    private Long id;
    private String orderNo;
    private Long skuId;
    private String skuTitle;
    private BigDecimal salePrice;
    private Integer quantity;
    private BigDecimal amount;
}