package com.example.retailflow.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecordEntity {
    private Long id;
    private String orderNo;
    private String payChannel;
    private String payStatus;
    private BigDecimal payAmount;
    private String thirdTradeNo;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
