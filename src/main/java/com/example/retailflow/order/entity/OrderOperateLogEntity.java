package com.example.retailflow.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_operate_log")
public class OrderOperateLogEntity {
    private Long id;
    private String orderNo;
    private String operateType;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;
}
