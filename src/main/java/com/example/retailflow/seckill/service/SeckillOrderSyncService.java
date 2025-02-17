package com.example.retailflow.seckill.service;

public interface SeckillOrderSyncService {
    void handleOrderPaid(String orderNo);

    void handleOrderCanceled(String orderNo);
}
