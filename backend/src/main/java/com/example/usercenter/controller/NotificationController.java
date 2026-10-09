package com.example.usercenter.controller;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.R;
import com.example.usercenter.dto.request.NotificationQuery;
import com.example.usercenter.dto.response.NotificationResponse;
import com.example.usercenter.dto.response.UnreadCountResponse;
import com.example.usercenter.security.SecurityUtils;
import com.example.usercenter.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "站内通知")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知列表（unreadOnly=true 只看未读）")
    @GetMapping
    public R<PageResult<NotificationResponse>> page(@ModelAttribute NotificationQuery query) {
        return R.ok(notificationService.page(SecurityUtils.requireUserId(), query));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public R<UnreadCountResponse> unreadCount() {
        return R.ok(new UnreadCountResponse(notificationService.unreadCount(SecurityUtils.requireUserId())));
    }

    @Operation(summary = "标记单条已读")
    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(SecurityUtils.requireUserId(), id);
        return R.ok();
    }

    @Operation(summary = "全部标记已读，返回受影响条数")
    @PostMapping("/read-all")
    public R<Integer> markAllRead() {
        return R.ok(notificationService.markAllRead(SecurityUtils.requireUserId()));
    }
}
