package com.example.retailflow.order.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OrderOperateLogResponse {
    private String operateType;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;
}
