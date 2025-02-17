package com.example.retailflow.admin.service;

import com.example.retailflow.admin.dto.DashboardOverviewResponse;
import com.example.retailflow.admin.dto.HotProductResponse;
import com.example.retailflow.admin.dto.StockAlertHistoryResponse;
import com.example.retailflow.admin.dto.StockAlertResponse;

import java.util.List;

public interface AdminDashboardService {
    DashboardOverviewResponse overview();

    List<HotProductResponse> hotProducts();

    List<StockAlertResponse> stockAlerts();

    List<StockAlertHistoryResponse> stockAlertHistory(Integer limit);
}
