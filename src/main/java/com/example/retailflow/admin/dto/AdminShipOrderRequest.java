package com.example.retailflow.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminShipOrderRequest {
    @NotBlank
    private String logisticsCompany;

    @NotBlank
    private String logisticsNo;

    private String remark;
}
