package com.example.usercenter.audit;

import com.example.usercenter.entity.AuditLog;
import com.example.usercenter.mapper.AuditLogMapper;
import com.example.usercenter.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计落库。审计失败绝不影响主流程（记日志即可），且 detail 一律截断、
 * 调用方有责任不把密码/令牌写进 detail。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditLogMapper auditLogMapper;

    public void record(String action, String targetType, String targetId, boolean success, String detail) {
        record(SecurityUtils.currentUserIdOrNull(), SecurityUtils.currentUsernameOrNull(),
                action, targetType, targetId, success, detail);
    }

    public void record(Long userId, String username, String action, String targetType, String targetId,
                       boolean success, String detail) {
        try {
            HttpServletRequest request = currentRequest();
            AuditLog entity = new AuditLog();
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setAction(action);
            entity.setTargetType(targetType == null || targetType.isBlank() ? null : targetType);
            entity.setTargetId(targetId);
            entity.setIp(clientIp(request));
            entity.setUserAgent(userAgent(request));
            entity.setSuccess(success);
            entity.setDetail(truncate(detail));
            auditLogMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("写审计日志失败 action={} targetId={}", action, targetId, ex);
        }
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= AuditLog.DETAIL_MAX_LENGTH
                ? detail
                : detail.substring(0, AuditLog.DETAIL_MAX_LENGTH);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return trimTo(first.trim(), 45);
        }
        return trimTo(request.getRemoteAddr(), 45);
    }

    private String userAgent(HttpServletRequest request) {
        return request == null ? null : trimTo(request.getHeader("User-Agent"), 255);
    }

    private String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
