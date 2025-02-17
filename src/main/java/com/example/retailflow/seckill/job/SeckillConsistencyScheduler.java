package com.example.retailflow.seckill.job;

import com.example.retailflow.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillConsistencyScheduler {

    private final SeckillService seckillService;

    @Scheduled(cron = "${app.seckill.reconcile-cron:0 */5 * * * ?}", scheduler = "seckillTaskScheduler")
    public void reconcileRecentActivities() {
        try {
            seckillService.reconcileRecentActivities();
        } catch (Exception ex) {
            log.error("reconcile seckill redis consistency failed", ex);
        }
    }
}
