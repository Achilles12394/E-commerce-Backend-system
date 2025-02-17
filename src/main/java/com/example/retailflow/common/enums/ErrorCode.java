package com.example.retailflow.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "success"),
    PARAM_ERROR(4001, "参数错误"),
    UNAUTHORIZED(4003, "未授权"),
    NOT_FOUND(4004, "资源不存在"),
    STOCK_NOT_ENOUGH(4090, "库存不足"),
    REPEAT_SUBMIT(4091, "重复提交"),
    ORDER_STATUS_ILLEGAL(4092, "订单状态非法"),
    SYSTEM_ERROR(5000, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}