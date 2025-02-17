package com.example.retailflow.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_activity")
public class SeckillActivityEntity {
    private Long id;
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
