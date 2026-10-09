package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.usercenter.audit.AuditRecorder;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.dto.request.ChangePasswordRequest;
import com.example.usercenter.dto.request.DeleteAccountRequest;
import com.example.usercenter.dto.request.UpdateProfileRequest;
import com.example.usercenter.dto.response.UserResponse;
import com.example.usercenter.entity.Notification;
import com.example.usercenter.entity.User;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.security.CookieService;
import com.example.usercenter.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 个人资料与账号安全。
 * 注意：所有"把字段清空"的更新都走 LambdaUpdateWrapper 显式 set(null)，
 * 因为 MyBatis-Plus 的 updateById 默认忽略 null 字段，否则清空操作会静默失效。
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserMapper userMapper;
    private final RoleService roleService;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final CookieService cookieService;
    private final AuditRecorder auditRecorder;

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        Long userId = SecurityUtils.requireUserId();
        User current = requireUser(userId);

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(current.getEmail())
                && userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email))) {
            throw BizException.field(ErrorCode.EMAIL_EXISTS, "email", "邮箱已被注册");
        }
        String phone = blankToNull(request.getPhone());
        if (phone != null && !phone.equals(current.getPhone())
                && userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getPhone, phone))) {
            throw BizException.field(ErrorCode.PHONE_EXISTS, "phone", "手机号已被注册");
        }

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getNickname, request.getNickname().trim())
                .set(User::getEmail, email)
                .set(User::getPhone, phone)
                .set(User::getGender, blankToNull(request.getGender()))
                .set(User::getBirthday, request.getBirthday())
                .set(User::getBio, blankToNull(request.getBio())));

        auditRecorder.record(userId, current.getUsername(), "PROFILE_UPDATE", "USER",
                String.valueOf(userId), true, null);
        return UserResponse.from(requireUser(userId), roleService.roleCodesOf(userId));
    }

    /** 失败审计（原密码错误）必须落库，因此排除 BizException 的回滚。 */
    @Transactional(noRollbackFor = BizException.class)
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.requireUserId();
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            auditRecorder.record(userId, user.getUsername(), "PASSWORD_CHANGE", "USER",
                    String.valueOf(userId), false, "原密码错误");
            throw BizException.of(ErrorCode.OLD_PASSWORD_WRONG);
        }
        passwordPolicy.validate(request.getNewPassword());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw BizException.of(ErrorCode.PASSWORD_POLICY, "新密码不能与原密码相同");
        }

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, passwordEncoder.encode(request.getNewPassword())));

        Long currentSessionId = SecurityUtils.currentPrincipal().map(p -> p.getSessionId()).orElse(null);
        int revoked = sessionService.revokeOthers(userId, currentSessionId);
        notificationService.create(userId, "密码已修改",
                "你的账号密码刚刚被修改，其他设备上的登录已全部失效。如非本人操作，请立即联系管理员。",
                Notification.TYPE_SECURITY);
        auditRecorder.record(userId, user.getUsername(), "PASSWORD_CHANGE", "USER",
                String.valueOf(userId), true, "其他会话失效数=" + revoked);
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequest request, HttpServletResponse response) {
        Long userId = SecurityUtils.requireUserId();
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BizException.of(ErrorCode.OLD_PASSWORD_WRONG, "密码不正确，无法注销");
        }
        List<String> roleCodes = roleService.roleCodesOf(userId);
        if (roleService.hasAdminRole(roleCodes) && roleService.countActiveUsersWithRole("ADMIN") <= 1) {
            throw BizException.of(ErrorCode.LAST_ADMIN);
        }

        sessionService.revokeAll(userId);
        userMapper.deleteById(userId);
        cookieService.clearRefreshCookie(response);
        auditRecorder.record(userId, user.getUsername(), "ACCOUNT_DELETE", "USER",
                String.valueOf(userId), true, null);
    }

    public UserResponse currentProfile() {
        Long userId = SecurityUtils.requireUserId();
        return UserResponse.from(requireUser(userId), roleService.roleCodesOf(userId));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
