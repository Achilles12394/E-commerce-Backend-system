package com.example.retailflow.seckill.controller;

import com.example.retailflow.common.api.ApiResponse;
import com.example.retailflow.seckill.dto.CreateSeckillActivityRequest;
import com.example.retailflow.seckill.dto.CreateSeckillActivitySkuRequest;
import com.example.retailflow.seckill.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;

    @PostMapping("/api/admin/seckill/activities")
    public ApiResponse<Long> createActivity(@Valid @RequestBody CreateSeckillActivityRequest request) {
        return ApiResponse.success(seckillService.createActivity(request));
    }

    @PostMapping("/api/admin/seckill/activities/{activityId}/items")
    public ApiResponse<Long> addItem(@PathVariable Long activityId,
                                     @Valid @RequestBody CreateSeckillActivitySkuRequest request) {
        return ApiResponse.success(seckillService.addActivitySku(activityId, request));
    }

    @PostMapping("/api/admin/seckill/activities/{activityId}/publish")
    public ApiResponse<Void> publish(@PathVariable Long activityId) {
        seckillService.publishActivity(activityId);
        return ApiResponse.success();
    }
}
