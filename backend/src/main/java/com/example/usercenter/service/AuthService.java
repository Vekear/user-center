package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.usercenter.audit.AuditRecorder;
import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.config.UcProperties;
import com.example.usercenter.dto.request.LoginRequest;
import com.example.usercenter.dto.request.RegisterRequest;
import com.example.usercenter.dto.response.LoginResponse;
import com.example.usercenter.dto.response.UserResponse;
import com.example.usercenter.entity.User;
import com.example.usercenter.entity.UserSession;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.security.CookieService;
import com.example.usercenter.security.JwtService;
import com.example.usercenter.security.SecurityUtils;
import com.example.usercenter.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 认证：注册、登录、刷新、登出。
 * 登录失败计数与锁定落在用户行上，重启不丢；refresh token 轮换（每次刷新换新令牌并作废旧的）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final DateTimeFormatter LOCK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserMapper userMapper;
    private final RoleService roleService;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final AuditRecorder auditRecorder;
    private final UcProperties properties;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        passwordPolicy.validate(request.getPassword());

        if (existsByUsername(username)) {
            throw BizException.field(ErrorCode.USERNAME_EXISTS, "username", "用户名已存在");
        }
        if (existsByEmail(email)) {
            throw BizException.field(ErrorCode.EMAIL_EXISTS, "email", "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? username
                : request.getNickname().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(User.STATUS_ACTIVE);
        user.setFailedAttempts(0);
        user.setDeleted(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 并发注册：唯一索引兜底，按索引名回填到具体字段
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("uk_user_username")) {
                throw BizException.field(ErrorCode.USERNAME_EXISTS, "username", "用户名已存在");
            }
            throw BizException.field(ErrorCode.EMAIL_EXISTS, "email", "邮箱已被注册");
        }

        roleService.assignDefault(user.getId());
        notificationService.createWelcome(user);
        auditRecorder.record(user.getId(), username, "AUTH_REGISTER", "USER", String.valueOf(user.getId()),
                true, null);
        return UserResponse.from(user, List.of("ROLE_USER"));
    }

    /**
     * noRollbackFor = BizException：登录失败时"失败次数 +1"和失败审计必须落库，
     * 否则 5 次锁定永远不会触发（默认回滚会把计数一起撤销）。
     */
    @Transactional(noRollbackFor = BizException.class)
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.getUsername().trim();
        User user = findByIdentifier(identifier);
        if (user == null) {
            auditRecorder.record(null, identifier, "AUTH_LOGIN", "USER", null, false, "用户不存在");
            throw BizException.of(ErrorCode.BAD_CREDENTIALS);
        }
        if (User.STATUS_DISABLED.equals(user.getStatus())) {
            auditRecorder.record(user.getId(), user.getUsername(), "AUTH_LOGIN", "USER",
                    String.valueOf(user.getId()), false, "账号已禁用");
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditRecorder.record(user.getId(), user.getUsername(), "AUTH_LOGIN", "USER",
                    String.valueOf(user.getId()), false, "账号锁定中");
            throw BizException.of(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请于 " + user.getLockedUntil().format(LOCK_TIME_FORMAT) + " 后重试");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user, now);
            throw BizException.of(ErrorCode.BAD_CREDENTIALS);
        }

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getFailedAttempts, 0)
                .set(User::getLockedUntil, null)
                .set(User::getLastLoginAt, now));

        List<String> roleCodes = roleService.roleCodesOf(user.getId());
        var issued = sessionService.create(user.getId(), httpRequest, Boolean.TRUE.equals(request.getRemember()));
        cookieService.writeRefreshCookie(httpResponse, issued.refreshToken(), issued.ttl());
        auditRecorder.record(user.getId(), user.getUsername(), "AUTH_LOGIN", "USER",
                String.valueOf(user.getId()), true, null);

        UserPrincipal principal = UserPrincipal.of(user.getId(), user.getUsername(), user.getStatus(),
                roleCodes, issued.sessionId());
        return LoginResponse.builder()
                .accessToken(jwtService.createAccessToken(principal))
                .expiresIn(jwtService.accessTokenSeconds())
                .user(UserResponse.from(user, roleCodes))
                .build();
    }

    /** 同样不能让"吊销会话"被回滚：账号被禁用时也要把该会话真正作废掉。 */
    @Transactional(noRollbackFor = BizException.class)
    public LoginResponse refresh(String rawToken, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UserSession session = sessionService.findByRawToken(rawToken).orElse(null);
        if (session == null || !sessionService.isUsable(session)) {
            cookieService.clearRefreshCookie(httpResponse);
            throw BizException.of(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        User user = userMapper.selectById(session.getUserId());
        if (user == null) {
            sessionService.revoke(session.getId());
            cookieService.clearRefreshCookie(httpResponse);
            throw BizException.of(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        if (User.STATUS_DISABLED.equals(user.getStatus())) {
            sessionService.revoke(session.getId());
            cookieService.clearRefreshCookie(httpResponse);
            throw BizException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        boolean remember = session.getCreatedAt() != null && session.getExpiresAt() != null
                && Duration.between(session.getCreatedAt(), session.getExpiresAt()).toDays() > 1;

        // 轮换：旧的作废，新的写入 Cookie，令牌被重放时旧的一定已失效
        sessionService.revoke(session.getId());
        var issued = sessionService.create(user.getId(), httpRequest, remember);
        cookieService.writeRefreshCookie(httpResponse, issued.refreshToken(), issued.ttl());

        List<String> roleCodes = roleService.roleCodesOf(user.getId());
        UserPrincipal principal = UserPrincipal.of(user.getId(), user.getUsername(), user.getStatus(),
                roleCodes, issued.sessionId());
        return LoginResponse.builder()
                .accessToken(jwtService.createAccessToken(principal))
                .expiresIn(jwtService.accessTokenSeconds())
                .user(UserResponse.from(user, roleCodes))
                .build();
    }

    @Transactional
    public void logout(String rawToken, HttpServletResponse httpResponse) {
        sessionService.findByRawToken(rawToken).ifPresent(session -> {
            sessionService.revoke(session.getId());
            auditRecorder.record(session.getUserId(), null, "AUTH_LOGOUT", "SESSION",
                    String.valueOf(session.getId()), true, null);
        });
        cookieService.clearRefreshCookie(httpResponse);
    }

    public UserResponse me() {
        Long userId = SecurityUtils.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return UserResponse.from(user, roleService.roleCodesOf(userId));
    }

    private void handleFailedAttempt(User user, LocalDateTime now) {
        int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
        int max = properties.getLogin().getMaxFailedAttempts();
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, user.getId());
        if (attempts >= max) {
            LocalDateTime lockedUntil = now.plusMinutes(properties.getLogin().getLockMinutes());
            update.set(User::getFailedAttempts, 0).set(User::getLockedUntil, lockedUntil);
            userMapper.update(null, update);
            auditRecorder.record(user.getId(), user.getUsername(), "AUTH_LOGIN", "USER",
                    String.valueOf(user.getId()), false, "密码错误次数过多，账号锁定至 " + lockedUntil);
            throw BizException.of(ErrorCode.ACCOUNT_LOCKED,
                    "密码错误次数过多，账号已锁定 " + properties.getLogin().getLockMinutes() + " 分钟");
        }
        update.set(User::getFailedAttempts, attempts);
        userMapper.update(null, update);
        auditRecorder.record(user.getId(), user.getUsername(), "AUTH_LOGIN", "USER",
                String.valueOf(user.getId()), false, "密码错误（第 " + attempts + " 次）");
    }

    private User findByIdentifier(String identifier) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .and(wrapper -> wrapper.eq(User::getUsername, identifier)
                        .or()
                        .eq(User::getEmail, identifier.toLowerCase(Locale.ROOT)))
                .last("LIMIT 1"));
    }

    private boolean existsByUsername(String username) {
        return userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private boolean existsByEmail(String email) {
        return userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }
}
