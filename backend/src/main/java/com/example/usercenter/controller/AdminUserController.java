package com.example.usercenter.controller;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.R;
import com.example.usercenter.dto.request.AdminUserQuery;
import com.example.usercenter.dto.request.UpdateRolesRequest;
import com.example.usercenter.dto.request.UpdateStatusRequest;
import com.example.usercenter.dto.response.StatsResponse;
import com.example.usercenter.dto.response.TempPasswordResponse;
import com.example.usercenter.dto.response.UserResponse;
import com.example.usercenter.security.SecurityUtils;
import com.example.usercenter.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@Tag(name = "管理后台-用户")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表：keyword/status/role 过滤 + 分页")
    @GetMapping("/users")
    public R<PageResult<UserResponse>> page(@ModelAttribute AdminUserQuery query) {
        return R.ok(adminUserService.page(query));
    }

    @Operation(summary = "活跃用户 id 列表（群发通知选人用）")
    @GetMapping("/users/active-ids")
    public R<List<Long>> activeIds(@RequestParam(defaultValue = "200") int limit) {
        return R.ok(adminUserService.activeUserIds(limit));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/users/{id}")
    public R<UserResponse> get(@PathVariable Long id) {
        return R.ok(adminUserService.get(id));
    }

    @Operation(summary = "启用/停用（不能操作自己，不能停用最后一个可用管理员）")
    @PatchMapping("/users/{id}/status")
    public R<UserResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return R.ok(adminUserService.updateStatus(id, request.getStatus(), SecurityUtils.requireUserId()));
    }

    @Operation(summary = "修改角色（不能改自己的角色，不能移除最后一个可用管理员）")
    @PutMapping("/users/{id}/roles")
    public R<UserResponse> updateRoles(@PathVariable Long id, @Valid @RequestBody UpdateRolesRequest request) {
        return R.ok(adminUserService.updateRoles(id, request.getRoles(), SecurityUtils.requireUserId()));
    }

    @Operation(summary = "重置密码：返回一次性临时密码，并吊销该用户全部会话")
    @PostMapping("/users/{id}/reset-password")
    public R<TempPasswordResponse> resetPassword(@PathVariable Long id) {
        return R.ok(new TempPasswordResponse(adminUserService.resetPassword(id, SecurityUtils.requireUserId())));
    }

    @Operation(summary = "概览统计")
    @GetMapping("/stats")
    public R<StatsResponse> stats() {
        return R.ok(adminUserService.stats());
    }
}
