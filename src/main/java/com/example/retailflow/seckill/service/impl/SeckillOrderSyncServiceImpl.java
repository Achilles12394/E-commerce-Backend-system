package com.example.retailflow.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retailflow.seckill.entity.SeckillOrderEntity;
import com.example.retailflow.seckill.mapper.SeckillOrderMapper;
import com.example.retailflow.seckill.service.SeckillOrderSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeckillOrderSyncServiceImpl implements SeckillOrderSyncService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillRedisService seckillRedisService;

    @Override
    @Transactional
    public void handleOrderPaid(String orderNo) {
        SeckillOrderEntity order = seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getOrderNo, orderNo));
        if (order == null || !"CREATED".equals(order.getStatus())) {
            return;
        }
        order.setStatus("PAID");
        seckillOrderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void handleOrderCanceled(String orderNo) {
        SeckillOrderEntity order = seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrderEntity>()
                .eq(SeckillOrderEntity::getOrderNo, orderNo));
        if (order == null || !"CREATED".equals(order.getStatus())) {
            return;
        }
        seckillRedisService.rollback(order.getActivityId(), order.getSkuId(), order.getUserId(), order.getQuantity());
        order.setStatus("RELEASED");
        seckillOrderMapper.updateById(order);
    }
}
