package com.example.retailflow.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("category")
public class CategoryEntity {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sortNo;
    private Integer status;
    private Integer deleted;
}