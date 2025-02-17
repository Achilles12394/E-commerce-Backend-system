package com.example.retailflow.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retailflow.auth.dto.LoginRequest;
import com.example.retailflow.auth.dto.LoginResponse;
import com.example.retailflow.auth.dto.RefreshTokenRequest;
import com.example.retailflow.auth.dto.RegisterRequest;
import com.example.retailflow.auth.dto.UserMeResponse;
import com.example.retailflow.auth.entity.UserEntity;
import com.example.retailflow.auth.entity.UserRoleEntity;
import com.example.retailflow.auth.mapper.RoleMapper;
import com.example.retailflow.auth.mapper.UserMapper;
import com.example.retailflow.auth.mapper.UserRoleMapper;
import com.example.retailflow.auth.security.JwtTokenProvider;
import com.example.retailflow.auth.service.AuthService;
import com.example.retailflow.common.context.JwtUser;
import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REFRESH_SESSION_KEY_PREFIX = "auth:refresh:session:";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.getUsername()));
        if (existing != null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setId(System.currentTimeMillis());
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        userMapper.insert(user);

        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setId(System.nanoTime());
        userRole.setUserId(user.getId());
        userRole.setRoleId(1002L);
        userRoleMapper.insert(userRole);
    }

    @Override
    public LoginResponse login(LoginRequest request, boolean adminLogin) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        List<String> roleCodes = roleMapper.findRoleCodesByUserId(user.getId());
        if (adminLogin && roleCodes.stream().noneMatch("ADMIN"::equals)) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "非管理员账号");
        }
        return issueTokenPair(user, roleCodes);
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            String sessionId = jwtTokenProvider.parseAllowExpired(token).get("sessionId", String.class);
            if (sessionId != null && !sessionId.isBlank()) {
                stringRedisTemplate.delete(refreshSessionKey(sessionId));
            }
        } catch (Exception ignored) {
            // Ignore invalid access tokens during logout.
        }
    }

    @Override
    public LoginResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtTokenProvider.valid(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "refresh token 无效");
        }
        String sessionId = jwtTokenProvider.parse(refreshToken).get("sessionId", String.class);
        String cachedToken = stringRedisTemplate.opsForValue().get(refreshSessionKey(sessionId));
        if (cachedToken == null || !cachedToken.equals(refreshToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "refresh token 已失效");
        }
        Long userId = jwtTokenProvider.parse(refreshToken).get("userId", Long.class);
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户状态无效");
        }
        List<String> roleCodes = roleMapper.findRoleCodesByUserId(user.getId());
        stringRedisTemplate.delete(refreshSessionKey(sessionId));
        return issueTokenPair(user, roleCodes);
    }

    @Override
    public UserMeResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUser jwtUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        List<String> roles = roleMapper.findRoleCodesByUserId(jwtUser.getUserId());
        return UserMeResponse.builder()
                .userId(jwtUser.getUserId())
                .username(jwtUser.getUsername())
                .roles(roles)
                .build();
    }

    private LoginResponse issueTokenPair(UserEntity user, List<String> roleCodes) {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roleCodes, sessionId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(), sessionId);
        stringRedisTemplate.opsForValue().set(
                refreshSessionKey(sessionId),
                refreshToken,
                jwtTokenProvider.refreshExpireSeconds(),
                java.util.concurrent.TimeUnit.SECONDS
        );
        return LoginResponse.builder()
                .accessToken(accessToken)
                .accessExpireIn(jwtTokenProvider.accessExpireSeconds())
                .refreshToken(refreshToken)
                .refreshExpireIn(jwtTokenProvider.refreshExpireSeconds())
                .tokenType("Bearer")
                .build();
    }

    private String refreshSessionKey(String sessionId) {
        return REFRESH_SESSION_KEY_PREFIX + sessionId;
    }
}
