package com.example.retailflow.seckill.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SeckillActivityItemResponse {
    private Long skuId;
    private String skuTitle;
    private BigDecimal seckillPrice;
    private BigDecimal originPrice;
    private Integer seckillStock;
    private Integer limitPerUser;
    private Integer status;
}
