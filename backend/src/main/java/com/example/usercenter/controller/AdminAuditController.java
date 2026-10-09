package com.example.usercenter.controller;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.R;
import com.example.usercenter.dto.request.AuditLogQuery;
import com.example.usercenter.dto.response.AuditLogResponse;
import com.example.usercenter.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台-审计")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditLogService auditLogService;

    @Operation(summary = "审计日志：userId/action/success/from/to 过滤 + 分页")
    @GetMapping
    public R<PageResult<AuditLogResponse>> page(@ModelAttribute AuditLogQuery query) {
        return R.ok(auditLogService.page(query));
    }
}
