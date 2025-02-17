package com.example.retailflow.seckill.controller;

import com.example.retailflow.common.api.ApiResponse;
import com.example.retailflow.common.context.SecurityUtils;
import com.example.retailflow.order.dto.OrderSubmitResponse;
import com.example.retailflow.seckill.dto.SeckillActivityResponse;
import com.example.retailflow.seckill.dto.SeckillSubmitRequest;
import com.example.retailflow.seckill.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @GetMapping("/api/seckill/activities")
    public ApiResponse<List<SeckillActivityResponse>> activities() {
        return ApiResponse.success(seckillService.listAvailableActivities());
    }

    @PostMapping("/api/seckill/activities/{activityId}/sku/{skuId}/submit")
    public ApiResponse<OrderSubmitResponse> submit(@PathVariable Long activityId,
                                                   @PathVariable Long skuId,
                                                   @Valid @RequestBody SeckillSubmitRequest request) {
        return ApiResponse.success(seckillService.submit(SecurityUtils.currentUserId(), activityId, skuId, request.getQuantity()));
    }
}
