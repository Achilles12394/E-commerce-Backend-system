package com.example.retailflow.admin.dto;

import lombok.Data;

@Data
public class AdminOrderQueryRequest {
    private String orderNo;
    private String status;
    private Long userId;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
