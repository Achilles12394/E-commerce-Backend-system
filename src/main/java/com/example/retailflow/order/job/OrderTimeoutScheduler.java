package com.example.retailflow.order.job;

import com.example.retailflow.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(cron = "0 */5 * * * ?", scheduler = "orderTaskScheduler")
    public void closeTimeoutOrders() {
        try {
            orderService.closeTimeoutOrders();
        } catch (Exception ex) {
            log.error("close timeout orders failed", ex);
        }
    }
}
