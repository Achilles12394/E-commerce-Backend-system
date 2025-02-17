package com.example.retailflow.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("cart_item")
public class CartItemEntity {
    private Long id;
    private Long userId;
    private Long skuId;
    private String skuTitle;
    private BigDecimal price;
    private Integer quantity;
    private Integer checked;
}