package com.example.retailflow.seckill.service;

import com.example.retailflow.order.dto.OrderSubmitResponse;
import com.example.retailflow.seckill.dto.CreateSeckillActivityRequest;
import com.example.retailflow.seckill.dto.CreateSeckillActivitySkuRequest;
import com.example.retailflow.seckill.dto.SeckillActivityResponse;

import java.util.List;

public interface SeckillService {
    Long createActivity(CreateSeckillActivityRequest request);

    Long addActivitySku(Long activityId, CreateSeckillActivitySkuRequest request);

    void publishActivity(Long activityId);

    List<SeckillActivityResponse> listAvailableActivities();

    void preheatUpcomingActivities();

    void reconcileRecentActivities();

    OrderSubmitResponse submit(Long userId, Long activityId, Long skuId, Integer quantity);
}
