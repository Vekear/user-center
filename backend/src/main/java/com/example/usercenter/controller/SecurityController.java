package com.example.usercenter.controller;

import com.example.usercenter.audit.Audit;
import com.example.usercenter.common.R;
import com.example.usercenter.dto.request.DeleteAccountRequest;
import com.example.usercenter.dto.response.SessionResponse;
import com.example.usercenter.entity.UserSession;
import com.example.usercenter.security.CookieService;
import com.example.usercenter.security.SecurityUtils;
import com.example.usercenter.security.UserPrincipal;
import com.example.usercenter.service.ProfileService;
import com.example.usercenter.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "账号安全")
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SessionService sessionService;
    private final ProfileService profileService;
    private final CookieService cookieService;

    @Operation(summary = "登录设备/会话列表")
    @GetMapping("/sessions")
    public R<List<SessionResponse>> sessions() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        return R.ok(sessionService.list(principal.getId(), principal.getSessionId()));
    }

    @Operation(summary = "退出指定设备")
    @DeleteMapping("/sessions/{id}")
    @Audit(action = "SESSION_REVOKE", targetType = "SESSION")
    public R<Void> revoke(@PathVariable Long id, HttpServletResponse response) {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        UserSession session = sessionService.requireOwnedSession(principal.getId(), id);
        sessionService.revoke(session.getId());
        if (session.getId().equals(principal.getSessionId())) {
            cookieService.clearRefreshCookie(response);
        }
        return R.ok();
    }

    @Operation(summary = "退出其他所有设备（保留当前设备）")
    @DeleteMapping("/sessions/others")
    @Audit(action = "SESSION_REVOKE_OTHERS", targetType = "SESSION")
    public R<Integer> revokeOthers() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        return R.ok(sessionService.revokeOthers(principal.getId(), principal.getSessionId()));
    }

    @Operation(summary = "注销账号（逻辑删除，需密码确认；最后一个管理员不可注销）")
    @DeleteMapping("/account")
    public R<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request, HttpServletResponse response) {
        profileService.deleteAccount(request, response);
        return R.ok();
    }
}
