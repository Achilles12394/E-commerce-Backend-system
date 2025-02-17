package com.example.retailflow.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seckill_activity_sku")
public class SeckillActivitySkuEntity {
    private Long id;
    private Long activityId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private Integer limitPerUser;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
