package com.example.usercenter.dto.response;

import com.example.usercenter.entity.AuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String targetType;
    private String targetId;
    private String ip;
    private String userAgent;
    private boolean success;
    private String detail;
    private LocalDateTime createdAt;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .ip(log.getIp())
                .userAgent(log.getUserAgent())
                .success(Boolean.TRUE.equals(log.getSuccess()))
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
