package com.example.retailflow.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("role")
public class RoleEntity {
    private Long id;
    private String code;
    private String name;
}