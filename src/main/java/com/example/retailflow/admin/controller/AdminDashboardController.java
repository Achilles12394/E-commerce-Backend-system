package com.example.retailflow.admin.controller;

import com.example.retailflow.admin.dto.DashboardOverviewResponse;
import com.example.retailflow.admin.dto.HotProductResponse;
import com.example.retailflow.admin.dto.StockAlertHistoryResponse;
import com.example.retailflow.admin.dto.StockAlertResponse;
import com.example.retailflow.admin.service.AdminDashboardService;
import com.example.retailflow.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    @GetMapping("/hot-products")
    public ApiResponse<List<HotProductResponse>> hotProducts() {
        return ApiResponse.success(dashboardService.hotProducts());
    }

    @GetMapping("/stock-alerts")
    public ApiResponse<List<StockAlertResponse>> stockAlerts() {
        return ApiResponse.success(dashboardService.stockAlerts());
    }

    @GetMapping("/stock-alert-history")
    public ApiResponse<List<StockAlertHistoryResponse>> stockAlertHistory() {
        return ApiResponse.success(dashboardService.stockAlertHistory(20));
    }
}
