package com.example.retailflow.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class OrdersEntity {
    private Long id;
    private String orderNo;
    private Long userId;
    private String orderStatus;
    private BigDecimal totalAmount;
    private BigDecimal payableAmount;
    private LocalDateTime payTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}