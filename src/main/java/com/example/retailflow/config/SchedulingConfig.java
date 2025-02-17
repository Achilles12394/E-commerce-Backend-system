package com.example.retailflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    @Bean(name = "orderTaskScheduler")
    public ThreadPoolTaskScheduler orderTaskScheduler(
            @Value("${app.task.scheduler.order.pool-size:2}") int poolSize,
            @Value("${app.task.scheduler.order.thread-name-prefix:retailflow-order-scheduler-}") String threadNamePrefix,
            @Value("${app.task.scheduler.order.await-termination-seconds:30}") int awaitTerminationSeconds) {
        return buildScheduler(poolSize, threadNamePrefix, awaitTerminationSeconds);
    }

    @Bean(name = "inventoryTaskScheduler")
    public ThreadPoolTaskScheduler inventoryTaskScheduler(
            @Value("${app.task.scheduler.inventory.pool-size:2}") int poolSize,
            @Value("${app.task.scheduler.inventory.thread-name-prefix:retailflow-inventory-scheduler-}") String threadNamePrefix,
            @Value("${app.task.scheduler.inventory.await-termination-seconds:30}") int awaitTerminationSeconds) {
        return buildScheduler(poolSize, threadNamePrefix, awaitTerminationSeconds);
    }

    @Bean(name = "seckillTaskScheduler")
    public ThreadPoolTaskScheduler seckillTaskScheduler(
            @Value("${app.task.scheduler.seckill.pool-size:2}") int poolSize,
            @Value("${app.task.scheduler.seckill.thread-name-prefix:retailflow-seckill-scheduler-}") String threadNamePrefix,
            @Value("${app.task.scheduler.seckill.await-termination-seconds:30}") int awaitTerminationSeconds) {
        return buildScheduler(poolSize, threadNamePrefix, awaitTerminationSeconds);
    }

    private ThreadPoolTaskScheduler buildScheduler(int poolSize,
                                                   String threadNamePrefix,
                                                   int awaitTerminationSeconds) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(awaitTerminationSeconds);
        scheduler.setErrorHandler(ex -> log.error("Scheduled task execution failed", ex));
        scheduler.initialize();
        return scheduler;
    }
}
