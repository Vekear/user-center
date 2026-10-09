package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.config.UcProperties;
import com.example.usercenter.dto.response.SessionResponse;
import com.example.usercenter.entity.UserSession;
import com.example.usercenter.mapper.UserSessionMapper;
import com.example.usercenter.security.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 登录会话（设备）管理。refresh token 明文只存在于 Cookie，
 * 库里存哈希，因此即使库被读走也无法直接冒用。
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionMapper userSessionMapper;
    private final UcProperties properties;

    public record IssuedSession(Long sessionId, String refreshToken, Duration ttl, LocalDateTime expiresAt) {
    }

    public IssuedSession create(Long userId, HttpServletRequest request, boolean remember) {
        String rawToken = Tokens.newRefreshToken();
        Duration ttl = remember
                ? Duration.ofDays(properties.getJwt().getRefreshTokenDays())
                : Duration.ofDays(1);
        LocalDateTime expiresAt = LocalDateTime.now().plus(ttl);

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setRefreshTokenHash(Tokens.sha256Hex(rawToken));
        session.setUserAgent(trim(request == null ? null : request.getHeader("User-Agent"), 255));
        session.setDeviceName(describe(request == null ? null : request.getHeader("User-Agent")));
        session.setIp(clientIp(request));
        session.setLastSeenAt(LocalDateTime.now());
        session.setExpiresAt(expiresAt);
        session.setRevoked(false);
        userSessionMapper.insert(session);

        return new IssuedSession(session.getId(), rawToken, ttl, expiresAt);
    }

    public Optional<UserSession> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        UserSession session = userSessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshTokenHash, Tokens.sha256Hex(rawToken)));
        return Optional.ofNullable(session);
    }

    public boolean isUsable(UserSession session) {
        return session != null
                && !Boolean.TRUE.equals(session.getRevoked())
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(LocalDateTime.now());
    }

    public void touch(Long sessionId) {
        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setLastSeenAt(LocalDateTime.now());
        userSessionMapper.updateById(session);
    }

    public void revoke(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setRevoked(true);
        userSessionMapper.updateById(session);
    }

    public int revokeAll(Long userId) {
        return userSessionMapper.revokeAll(userId);
    }

    public int revokeOthers(Long userId, Long keepSessionId) {
        if (keepSessionId == null) {
            return userSessionMapper.revokeAll(userId);
        }
        return userSessionMapper.revokeOthers(userId, keepSessionId);
    }

    public List<SessionResponse> list(Long userId, Long currentSessionId) {
        return userSessionMapper.selectList(new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
                        .eq(UserSession::getRevoked, false)
                        .gt(UserSession::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(UserSession::getLastSeenAt))
                .stream()
                .map(session -> SessionResponse.from(session, currentSessionId))
                .toList();
    }

    public UserSession requireOwnedSession(Long userId, Long sessionId) {
        UserSession session = userSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    /**
     * 把 User-Agent 折成人类可读的设备名，前端列表用它做主标题。
     */
    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知设备";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String os;
        if (ua.contains("windows")) {
            os = "Windows";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            os = "iOS";
        } else if (ua.contains("mac os") || ua.contains("macintosh")) {
            os = "macOS";
        } else if (ua.contains("linux")) {
            os = "Linux";
        } else {
            os = "未知系统";
        }
        String browser;
        if (ua.contains("edg/")) {
            browser = "Edge";
        } else if (ua.contains("chrome/") && !ua.contains("chromium")) {
            browser = "Chrome";
        } else if (ua.contains("firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("safari/")) {
            browser = "Safari";
        } else if (ua.contains("curl")) {
            browser = "curl";
        } else {
            browser = "未知浏览器";
        }
        return browser + " · " + os;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return trim(comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim(), 45);
        }
        return trim(request.getRemoteAddr(), 45);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
