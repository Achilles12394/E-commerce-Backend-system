package com.example.retailflow.auth.service;

import com.example.retailflow.auth.dto.LoginRequest;
import com.example.retailflow.auth.dto.LoginResponse;
import com.example.retailflow.auth.dto.RefreshTokenRequest;
import com.example.retailflow.auth.dto.RegisterRequest;
import com.example.retailflow.auth.dto.UserMeResponse;

public interface AuthService {
    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request, boolean adminLogin);

    void logout(String token);

    LoginResponse refresh(RefreshTokenRequest request);

    UserMeResponse me();
}
