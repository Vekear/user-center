package com.example.usercenter.dto.response;

import com.example.usercenter.entity.UserSession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionResponse {

    private Long id;
    /** 是否为当前请求所用的会话 */
    private boolean current;
    private String deviceName;
    private String userAgent;
    private String ip;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public static SessionResponse from(UserSession session, Long currentSessionId) {
        return SessionResponse.builder()
                .id(session.getId())
                .current(session.getId().equals(currentSessionId))
                .deviceName(session.getDeviceName())
                .userAgent(session.getUserAgent())
                .ip(session.getIp())
                .lastSeenAt(session.getLastSeenAt())
                .createdAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .build();
    }
}
