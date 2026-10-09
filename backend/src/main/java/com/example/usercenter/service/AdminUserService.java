package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.audit.AuditRecorder;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.dto.request.AdminUserQuery;
import com.example.usercenter.dto.response.StatsResponse;
import com.example.usercenter.dto.response.UserResponse;
import com.example.usercenter.entity.Notification;
import com.example.usercenter.entity.User;
import com.example.usercenter.mapper.AuditLogMapper;
import com.example.usercenter.mapper.RoleMapper;
import com.example.usercenter.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理后台：用户列表/详情、启用禁用、改角色、重置密码、概览统计。
 * 三条不可退让的守卫：不能操作自己、不能移除最后一个可用管理员、重置密码必须吊销全部会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String TEMP_PASSWORD_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final AuditLogMapper auditLogMapper;
    private final RoleService roleService;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;

    public PageResult<UserResponse> page(AdminUserQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getNickname, keyword));
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(User::getStatus, query.getStatus().trim().toUpperCase());
        }
        if (query.getRole() != null && !query.getRole().isBlank()) {
            // normalize 只放行 ROLE_USER / ROLE_ADMIN，等价于白名单，不存在注入面
            String roleCode = roleService.normalize(query.getRole());
            wrapper.inSql(User::getId,
                    "SELECT ur.user_id FROM user_role ur JOIN role r ON r.id = ur.role_id WHERE r.code = '"
                            + roleCode + "'");
        }
        Page<User> page = userMapper.selectPage(
                new Page<>(query.normalizedPage(), query.normalizedSize()), wrapper);
        List<Long> ids = page.getRecords().stream().map(User::getId).toList();
        Map<Long, List<String>> rolesByUser = roleService.roleCodesOfUsers(ids);
        List<UserResponse> list = page.getRecords().stream()
                .map(user -> UserResponse.from(user, rolesByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return PageResult.of(list, page.getTotal(), query.normalizedPage(), query.normalizedSize());
    }

    public UserResponse get(Long userId) {
        User user = requireUser(userId);
        return UserResponse.from(user, roleService.roleCodesOf(userId));
    }

    @Transactional
    public UserResponse updateStatus(Long userId, String status, Long actorId) {
        User user = requireUser(userId);
        if (userId.equals(actorId)) {
            throw BizException.of(ErrorCode.SELF_OPERATION, "不能修改自己的账号状态");
        }
        String normalized = status.trim().toUpperCase();
        if (User.STATUS_DISABLED.equals(normalized) && isLastActiveAdmin(userId)) {
            throw BizException.of(ErrorCode.LAST_ADMIN);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, normalized));
        if (User.STATUS_DISABLED.equals(normalized)) {
            sessionService.revokeAll(userId);
        }
        notificationService.create(userId,
                User.STATUS_DISABLED.equals(normalized) ? "账号已被停用" : "账号已恢复",
                User.STATUS_DISABLED.equals(normalized)
                        ? "你的账号已被管理员停用，如有疑问请联系管理员。"
                        : "你的账号已恢复正常，可以继续使用。",
                Notification.TYPE_ACCOUNT);
        auditRecorder.record(actorId, null, "ADMIN_USER_STATUS", "USER", String.valueOf(userId), true,
                "status=" + normalized);
        return UserResponse.from(requireUser(userId), roleService.roleCodesOf(userId));
    }

    @Transactional
    public UserResponse updateRoles(Long userId, List<String> shortCodes, Long actorId) {
        requireUser(userId);
        if (userId.equals(actorId)) {
            throw BizException.of(ErrorCode.SELF_OPERATION, "不能修改自己的角色");
        }
        List<String> normalized = roleService.normalizeAll(shortCodes);
        boolean keepsAdmin = normalized.contains("ROLE_ADMIN");
        if (!keepsAdmin && isLastActiveAdmin(userId)) {
            throw BizException.of(ErrorCode.LAST_ADMIN);
        }
        roleService.assign(userId, shortCodes);
        notificationService.create(userId, "角色已变更",
                "你的账号角色已更新为：" + String.join("、", UserResponse.shortCodes(normalized)),
                Notification.TYPE_ACCOUNT);
        auditRecorder.record(actorId, null, "ADMIN_USER_ROLES", "USER", String.valueOf(userId), true,
                "roles=" + String.join(",", UserResponse.shortCodes(normalized)));
        return UserResponse.from(requireUser(userId), roleService.roleCodesOf(userId));
    }

    /**
     * 重置密码：返回一次性临时密码（只在这里出现，不落库明文），并吊销该用户全部会话。
     */
    @Transactional
    public String resetPassword(Long userId, Long actorId) {
        User user = requireUser(userId);
        String tempPassword = randomPassword(12);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, passwordEncoder.encode(tempPassword))
                .set(User::getFailedAttempts, 0)
                .set(User::getLockedUntil, null));
        int revoked = sessionService.revokeAll(userId);
        notificationService.create(userId, "密码已被重置",
                "管理员重置了你的密码，所有设备需要重新登录。请登录后立即修改密码。",
                Notification.TYPE_SECURITY);
        // 审计里绝不写临时密码明文
        auditRecorder.record(actorId, null, "ADMIN_USER_RESET_PASSWORD", "USER", String.valueOf(userId), true,
                "user=" + user.getUsername() + " 会话失效数=" + revoked);
        return tempPassword;
    }

    public StatsResponse stats() {
        return StatsResponse.builder()
                .userTotal(userMapper.countAll())
                .userActive(userMapper.countByStatus(User.STATUS_ACTIVE))
                .userDisabled(userMapper.countByStatus(User.STATUS_DISABLED))
                .notificationUnread(notificationService.unreadCountAllUsers())
                .auditToday(auditLogMapper.countOn(LocalDate.now()))
                .newUsers7d(userMapper.countCreatedSince(LocalDateTime.now().minusDays(7)))
                .build();
    }

    /** 活跃用户 id 列表，供管理端选择收件人 */
    public List<Long> activeUserIds(int limit) {
        return new ArrayList<>(userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getStatus, User.STATUS_ACTIVE)
                        .orderByDesc(User::getCreatedAt)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 500)))
                .stream().map(User::getId).toList());
    }

    private boolean isLastActiveAdmin(Long userId) {
        List<String> roles = roleService.roleCodesOf(userId);
        if (!roleService.hasAdminRole(roles)) {
            return false;
        }
        return roleMapper.countActiveUsersWithRole("ROLE_ADMIN") <= 1;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private String randomPassword(int length) {
        StringBuilder builder = new StringBuilder(length);
        builder.append((char) ('A' + RANDOM.nextInt(26)));
        builder.append((char) ('a' + RANDOM.nextInt(26)));
        builder.append((char) ('2' + RANDOM.nextInt(8)));
        for (int i = 3; i < length; i++) {
            builder.append(TEMP_PASSWORD_ALPHABET.charAt(RANDOM.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }
}
