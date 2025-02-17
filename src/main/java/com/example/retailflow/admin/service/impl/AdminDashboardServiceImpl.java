package com.example.retailflow.admin.service.impl;

import com.example.retailflow.admin.dto.DashboardOverviewResponse;
import com.example.retailflow.admin.dto.HotProductResponse;
import com.example.retailflow.admin.dto.StockAlertHistoryResponse;
import com.example.retailflow.admin.dto.StockAlertResponse;
import com.example.retailflow.admin.mapper.AdminDashboardMapper;
import com.example.retailflow.admin.service.AdminDashboardService;
import com.example.retailflow.inventory.entity.StockAlertRecordEntity;
import com.example.retailflow.inventory.mapper.StockAlertRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final AdminDashboardMapper adminDashboardMapper;
    private final StockAlertRecordMapper stockAlertRecordMapper;

    @Override
    public DashboardOverviewResponse overview() {
        return adminDashboardMapper.overview();
    }

    @Override
    public List<HotProductResponse> hotProducts() {
        return adminDashboardMapper.hotProducts();
    }

    @Override
    public List<StockAlertResponse> stockAlerts() {
        return adminDashboardMapper.stockAlerts();
    }

    @Override
    public List<StockAlertHistoryResponse> stockAlertHistory(Integer limit) {
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        return stockAlertRecordMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, safeLimit),
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockAlertRecordEntity>()
                                .orderByDesc(StockAlertRecordEntity::getId))
                .getRecords()
                .stream()
                .map(record -> StockAlertHistoryResponse.builder()
                        .skuId(record.getSkuId())
                        .availableStock(record.getAvailableStock())
                        .thresholdStock(record.getThresholdStock())
                        .alertTime(record.getCreatedAt())
                        .build())
                .toList();
    }
}
