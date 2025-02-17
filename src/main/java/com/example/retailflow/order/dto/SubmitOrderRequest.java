package com.example.retailflow.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitOrderRequest {
    @NotBlank
    private String token;
}