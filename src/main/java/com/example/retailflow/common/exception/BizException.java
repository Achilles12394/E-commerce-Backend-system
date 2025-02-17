package com.example.retailflow.common.exception;

import com.example.retailflow.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}