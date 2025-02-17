package com.example.retailflow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expire-seconds:1800}")
    private Long accessExpireSeconds;

    @Value("${jwt.refresh-expire-seconds:1209600}")
    private Long refreshExpireSeconds;

    public String generateAccessToken(Long userId, String username, List<String> roles, String sessionId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("tokenType", TOKEN_TYPE_ACCESS)
                .claim("sessionId", sessionId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessExpireSeconds * 1000))
                .signWith(secretKey())
                .compact();
    }

    public String generateRefreshToken(Long userId, String username, String sessionId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("tokenType", TOKEN_TYPE_REFRESH)
                .claim("sessionId", sessionId)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshExpireSeconds * 1000))
                .signWith(secretKey())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(secretKey()).build().parseSignedClaims(token).getPayload();
    }

    public Claims parseAllowExpired(String token) {
        try {
            return parse(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims();
        }
    }

    public boolean valid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            return TOKEN_TYPE_ACCESS.equals(parse(token).get("tokenType", String.class));
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            return TOKEN_TYPE_REFRESH.equals(parse(token).get("tokenType", String.class));
        } catch (Exception ex) {
            return false;
        }
    }

    public Long accessExpireSeconds() {
        return accessExpireSeconds;
    }

    public Long refreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    public long remainingSeconds(String token) {
        Claims claims = parseAllowExpired(token);
        Date expiration = claims.getExpiration();
        long diffMillis = expiration.getTime() - System.currentTimeMillis();
        return Math.max(diffMillis / 1000, 0);
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
