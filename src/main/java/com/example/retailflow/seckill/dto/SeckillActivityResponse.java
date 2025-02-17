package com.example.retailflow.seckill.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SeckillActivityResponse {
    private Long activityId;
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private List<SeckillActivityItemResponse> items;
}
