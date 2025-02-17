package com.example.retailflow.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserMeResponse {
    private Long userId;
    private String username;
    private List<String> roles;
}