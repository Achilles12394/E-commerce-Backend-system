package com.example.retailflow.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("idempotent_token")
public class IdempotentTokenEntity {
    private Long id;
    private String token;
    private Long userId;
    private String bizType;
    private LocalDateTime expiredAt;
    private Integer used;
}