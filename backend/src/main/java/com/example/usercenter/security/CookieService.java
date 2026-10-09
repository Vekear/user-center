package com.example.usercenter.security;

import com.example.usercenter.config.UcProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh token 的 Cookie 读写。httpOnly + SameSite，前端拿不到明文，也就不用担心 XSS 偷令牌。
 */
@Component
@RequiredArgsConstructor
public class CookieService {

    private final UcProperties properties;

    public String readRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        String name = properties.getCookie().getRefreshName();
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String buildSetCookieHeader(String token, Duration ttl) {
        return build(token, ttl).toString();
    }

    public String buildClearCookieHeader() {
        return build("", Duration.ZERO).toString();
    }

    private ResponseCookie build(String value, Duration ttl) {
        return ResponseCookie.from(properties.getCookie().getRefreshName(), value)
                .httpOnly(true)
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite())
                .path(properties.getCookie().getPath())
                .maxAge(ttl)
                .build();
    }

    public void writeRefreshCookie(jakarta.servlet.http.HttpServletResponse response, String token, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildSetCookieHeader(token, ttl));
    }

    public void clearRefreshCookie(jakarta.servlet.http.HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildClearCookieHeader());
    }
}
