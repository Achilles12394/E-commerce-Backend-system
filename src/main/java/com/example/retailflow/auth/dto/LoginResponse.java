package com.example.retailflow.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private Long accessExpireIn;
    private String refreshToken;
    private Long refreshExpireIn;
    private String tokenType;
}
