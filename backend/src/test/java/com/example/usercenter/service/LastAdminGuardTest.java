package com.example.usercenter.service;

import com.example.usercenter.common.BizException;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "系统至少保留一个可用管理员"这条守卫，需要把环境压到只剩一个活跃管理员才能触发，
 * 因此这个用例直接在事务里改库（结束自动回滚），不通过 HTTP。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LastAdminGuardTest {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Test
    void refusesToDisableOrDemoteTheOnlyActiveAdmin() {
        String username = "last_admin_" + System.nanoTime();
        jdbcTemplate.update("""
                        INSERT INTO `user` (username, email, password_hash, nickname, status, failed_attempts, deleted)
                        VALUES (?, ?, ?, ?, 'ACTIVE', 0, 0)
                        """,
                username, username + "@example.com", passwordEncoder.encode("Passw0rd123"), "唯一管理员");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, username);
        Long adminRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM role WHERE code = 'ROLE_ADMIN'", Long.class);
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, adminRoleId);

        // 把其他管理员全部停用，使该用户成为系统里唯一可用的管理员
        jdbcTemplate.update("UPDATE `user` SET status = 'DISABLED' WHERE id <> ?", userId);
        assertThat(userMapper.selectById(userId).getStatus()).isEqualTo("ACTIVE");

        assertThatThrownBy(() -> adminUserService.updateRoles(userId, List.of("USER"), 999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LAST_ADMIN);

        assertThatThrownBy(() -> adminUserService.updateStatus(userId, "DISABLED", 999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LAST_ADMIN);

        // 仍然是管理员且状态未被改动
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM `user` WHERE id = ?", String.class, userId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role_id = ?", Long.class,
                userId, adminRoleId)).isEqualTo(1L);
    }
}
