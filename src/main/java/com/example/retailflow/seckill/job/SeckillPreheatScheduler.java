package com.example.retailflow.seckill.job;

import com.example.retailflow.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillPreheatScheduler {

    private final SeckillService seckillService;

    @Scheduled(cron = "${app.seckill.preheat-cron:0 */1 * * * ?}", scheduler = "seckillTaskScheduler")
    public void preheatUpcomingActivities() {
        try {
            seckillService.preheatUpcomingActivities();
        } catch (Exception ex) {
            log.error("preheat upcoming seckill activities failed", ex);
        }
    }
}
