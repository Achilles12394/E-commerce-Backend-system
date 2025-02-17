package com.example.retailflow.common.exception;

import com.example.retailflow.common.api.ApiResponse;
import com.example.retailflow.common.enums.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException ex) {
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ApiResponse<Void> handleValidation(Exception ex) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(), ex.getMessage());
    }
}