package com.example.retailflow.auth.security;

import com.example.retailflow.auth.mapper.RoleMapper;
import com.example.retailflow.auth.mapper.UserMapper;
import com.example.retailflow.auth.entity.UserEntity;
import com.example.retailflow.common.context.JwtUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtTokenProvider.valid(token) && jwtTokenProvider.isAccessToken(token)) {
                Claims claims = jwtTokenProvider.parse(token);
                Long userId = claims.get("userId", Long.class);
                UserEntity user = userMapper.selectById(userId);
                if (user != null) {
                    List<String> roleCodes = roleMapper.findRoleCodesByUserId(userId);
                    List<SimpleGrantedAuthority> authorities = roleCodes.stream()
                            .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                            .toList();
                    JwtUser principal = new JwtUser(user.getId(), user.getUsername(), user.getPassword(), authorities);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
