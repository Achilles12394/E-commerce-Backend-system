package com.example.retailflow.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class UserEntity {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}