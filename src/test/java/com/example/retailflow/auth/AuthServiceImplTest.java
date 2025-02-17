package com.example.retailflow.auth;

import com.example.retailflow.auth.dto.LoginRequest;
import com.example.retailflow.auth.dto.RegisterRequest;
import com.example.retailflow.auth.entity.UserEntity;
import com.example.retailflow.auth.mapper.RoleMapper;
import com.example.retailflow.auth.mapper.UserMapper;
import com.example.retailflow.auth.mapper.UserRoleMapper;
import com.example.retailflow.auth.security.JwtTokenProvider;
import com.example.retailflow.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private AuthServiceImpl authService;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = Mockito.mock(UserMapper.class);
        UserRoleMapper userRoleMapper = Mockito.mock(UserRoleMapper.class);
        RoleMapper roleMapper = Mockito.mock(RoleMapper.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);

        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(roleMapper.findRoleCodesByUserId(any())).thenReturn(List.of("CUSTOMER"));
        when(tokenProvider.generateToken(any(), any(), any())).thenReturn("token");
        when(tokenProvider.expireSeconds()).thenReturn(7200L);

        authService = new AuthServiceImpl(userMapper, userRoleMapper, roleMapper, passwordEncoder, tokenProvider, redisTemplate);
    }

    @Test
    void testRegister() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new_user");
        request.setPassword("123456");
        request.setNickname("new");
        authService.register(request);
        assertTrue(true);
    }

    @Test
    void testLogin() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("user1");
        user.setPassword("encoded");
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("user1");
        request.setPassword("123456");
        assertNotNull(authService.login(request, false).getAccessToken());
    }
}