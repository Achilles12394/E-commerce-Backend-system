package com.example.retailflow.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("brand")
public class BrandEntity {
    private Long id;
    private String name;
    private String logoUrl;
    private Integer status;
    private Integer deleted;
}