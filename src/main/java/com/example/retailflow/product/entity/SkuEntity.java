package com.example.retailflow.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sku")
public class SkuEntity {
    private Long id;
    private String skuCode;
    private Long spuId;
    private String title;
    private BigDecimal price;
    private Long salesCount;
    private Integer stockStatus;
    private Integer deleted;
}