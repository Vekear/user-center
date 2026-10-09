package com.example.usercenter.controller;

import com.example.usercenter.audit.AuditRecorder;
import com.example.usercenter.common.R;
import com.example.usercenter.dto.request.SendNotificationRequest;
import com.example.usercenter.security.SecurityUtils;
import com.example.usercenter.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台-通知")
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final AuditRecorder auditRecorder;

    @Operation(summary = "群发通知：all=true 发给所有活跃用户，否则发给 userIds")
    @PostMapping
    public R<Integer> send(@Valid @RequestBody SendNotificationRequest request) {
        int sent = notificationService.send(request);
        auditRecorder.record(SecurityUtils.requireUserId(), SecurityUtils.currentUsernameOrNull(),
                "ADMIN_NOTIFICATION_SEND", "NOTIFICATION", null, true,
                "收件人数=" + sent + " 标题=" + request.getTitle());
        return R.ok(sent);
    }
}
