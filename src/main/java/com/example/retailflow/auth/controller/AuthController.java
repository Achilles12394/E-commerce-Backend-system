package com.example.retailflow.auth.controller;

import com.example.retailflow.auth.dto.LoginRequest;
import com.example.retailflow.auth.dto.LoginResponse;
import com.example.retailflow.auth.dto.RefreshTokenRequest;
import com.example.retailflow.auth.dto.RegisterRequest;
import com.example.retailflow.auth.dto.UserMeResponse;
import com.example.retailflow.auth.service.AuthService;
import com.example.retailflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request, false));
    }

    @PostMapping("/admin/login")
    public ApiResponse<LoginResponse> adminLogin(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request, true));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ApiResponse.success();
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me() {
        return ApiResponse.success(authService.me());
    }
}
