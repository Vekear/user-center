package com.example.usercenter.service;

import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 密码强度：8-64 位且同时包含字母与数字。规则只此一处，注册与改密共用。
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw BizException.of(ErrorCode.PASSWORD_POLICY,
                    "密码长度需为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 位");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw BizException.of(ErrorCode.PASSWORD_POLICY, "密码需同时包含字母和数字");
        }
    }
}
