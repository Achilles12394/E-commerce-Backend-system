package com.example.retailflow.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seckill_order")
public class SeckillOrderEntity {
    private Long id;
    private String orderNo;
    private Long activityId;
    private Long skuId;
    private Long userId;
    private Integer quantity;
    private BigDecimal seckillPrice;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
