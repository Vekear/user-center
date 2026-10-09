package com.example.usercenter.service;

import com.example.usercenter.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"Passw0rd123", "abc12345", "A1b2c3d4e5"})
    void acceptsLetterAndDigitCombinations(String password) {
        assertThatCode(() -> policy.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "alllowercase", "12345678", "        ", "a1"})
    void rejectsWeakPasswords(String password) {
        assertThatThrownBy(() -> policy.validate(password)).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsNullAndOverlong() {
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> policy.validate("a1" + "x".repeat(70))).isInstanceOf(BizException.class);
    }
}
