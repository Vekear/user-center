package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.dto.request.AuditLogQuery;
import com.example.usercenter.dto.response.AuditLogResponse;
import com.example.usercenter.entity.AuditLog;
import com.example.usercenter.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 审计日志查询（仅管理员）。
 * 时间参数用 ISO-8601 字符串接收，解析失败直接 400，不做静默兜底——审计查询悄悄换条件比报错更危险。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public PageResult<AuditLogResponse> page(AuditLogQuery query) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getCreatedAt);
        if (query.getUserId() != null) {
            wrapper.eq(AuditLog::getUserId, query.getUserId());
        }
        if (query.getAction() != null && !query.getAction().isBlank()) {
            wrapper.likeRight(AuditLog::getAction, query.getAction().trim().toUpperCase());
        }
        if (query.getSuccess() != null) {
            wrapper.eq(AuditLog::getSuccess, query.getSuccess());
        }
        LocalDateTime from = parseTime(query.getFrom(), "from");
        LocalDateTime to = parseTime(query.getTo(), "to");
        if (from != null) {
            wrapper.ge(AuditLog::getCreatedAt, from);
        }
        if (to != null) {
            wrapper.le(AuditLog::getCreatedAt, to);
        }

        Page<AuditLog> page = auditLogMapper.selectPage(
                new Page<>(query.normalizedPage(), query.normalizedSize()), wrapper);
        List<AuditLogResponse> list = page.getRecords().stream().map(AuditLogResponse::from).toList();
        return PageResult.of(list, page.getTotal(), query.normalizedPage(), query.normalizedSize());
    }

    private LocalDateTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw BizException.field(field, "时间格式需为 ISO-8601，例如 2026-10-09T00:00:00");
        }
    }
}
