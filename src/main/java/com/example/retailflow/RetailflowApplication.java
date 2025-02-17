package com.example.retailflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@MapperScan("com.example.retailflow.**.mapper")
@SpringBootApplication
public class RetailflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailflowApplication.class, args);
    }
}