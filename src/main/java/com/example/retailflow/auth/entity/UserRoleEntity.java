package com.example.retailflow.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_role")
public class UserRoleEntity {
    private Long id;
    private Long userId;
    private Long roleId;
}