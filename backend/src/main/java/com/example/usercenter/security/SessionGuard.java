package com.example.usercenter.security;

import com.example.usercenter.entity.UserSession;
import com.example.usercenter.mapper.UserSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 每个已认证请求都会问一句"这个会话还活着吗"。
 * 代价是一次主键查询；换来的是撤销会话立即生效（否则被踢下线的设备还能用旧 access token 活动到过期）。
 */
@Component
@RequiredArgsConstructor
public class SessionGuard {

    private final UserSessionMapper userSessionMapper;

    public boolean isActive(Long sessionId) {
        if (sessionId == null) {
            return false;
        }
        UserSession session = userSessionMapper.selectById(sessionId);
        if (session == null || Boolean.TRUE.equals(session.getRevoked())) {
            return false;
        }
        return session.getExpiresAt() != null && session.getExpiresAt().isAfter(LocalDateTime.now());
    }
}
