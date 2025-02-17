package com.example.retailflow.seckill.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateSeckillActivityRequest {
    @NotBlank
    private String activityName;
    @NotNull
    @FutureOrPresent
    private LocalDateTime startTime;
    @NotNull
    @Future
    private LocalDateTime endTime;
}
