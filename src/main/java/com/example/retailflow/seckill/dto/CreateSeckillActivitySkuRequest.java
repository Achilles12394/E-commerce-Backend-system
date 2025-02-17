package com.example.retailflow.seckill.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateSeckillActivitySkuRequest {
    @NotNull
    private Long skuId;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal seckillPrice;
    @NotNull
    @Min(1)
    private Integer seckillStock;
    @NotNull
    @Min(1)
    private Integer limitPerUser;
}
