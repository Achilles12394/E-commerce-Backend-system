package com.example.retailflow.admin.mapper;

import com.example.retailflow.admin.dto.DashboardOverviewResponse;
import com.example.retailflow.admin.dto.HotProductResponse;
import com.example.retailflow.admin.dto.StockAlertResponse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AdminDashboardMapper {
    DashboardOverviewResponse overview();

    List<HotProductResponse> hotProducts();

    List<StockAlertResponse> stockAlerts();
}