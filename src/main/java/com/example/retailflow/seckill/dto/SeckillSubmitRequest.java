package com.example.retailflow.seckill.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SeckillSubmitRequest {
    @NotNull
    @Min(1)
    private Integer quantity;
}
