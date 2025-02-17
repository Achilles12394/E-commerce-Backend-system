package com.example.retailflow.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("spu")
public class SpuEntity {
    private Long id;
    private String spuCode;
    private String title;
    private String subtitle;
    private Long categoryId;
    private Long brandId;
    private Integer publishStatus;
    private Integer deleted;
}