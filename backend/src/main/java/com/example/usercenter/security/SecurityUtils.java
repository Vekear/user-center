package com.example.usercenter.security;

import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 取当前登录主体的唯一入口，避免各处重复写 SecurityContextHolder。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static UserPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(() -> BizException.of(ErrorCode.UNAUTHORIZED));
    }

    public static Long requireUserId() {
        return requirePrincipal().getId();
    }

    public static Long currentUserIdOrNull() {
        return currentPrincipal().map(UserPrincipal::getId).orElse(null);
    }

    public static String currentUsernameOrNull() {
        return currentPrincipal().map(UserPrincipal::getUsername).orElse(null);
    }
}
