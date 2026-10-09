package com.example.usercenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.usercenter.config.UcProperties;
import com.example.usercenter.entity.Role;
import com.example.usercenter.entity.User;
import com.example.usercenter.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始管理员。角色字典由 db/seed.sql 插入；这里只负责创建管理员账号——
 * 因为 BCrypt 哈希必须由应用生成，不能把哈希常量固化进仓库。
 * 账号已存在则完全不动（不会覆盖你自己改过的密码）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserMapper userMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final UcProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (roleService.findAll().isEmpty()) {
            log.warn("角色字典为空，请确认 resources/db/seed.sql 是否执行成功");
            return;
        }
        String username = properties.getAdmin().getSeedUsername();
        if (username == null || username.isBlank()) {
            return;
        }
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists) {
            return;
        }
        String rawPassword = properties.getAdmin().getSeedPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("未配置 uc.admin.seed-password，跳过初始管理员创建");
            return;
        }
        passwordPolicy.validate(rawPassword);

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(properties.getAdmin().getSeedEmail());
        admin.setNickname(properties.getAdmin().getSeedNickname());
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setStatus(User.STATUS_ACTIVE);
        admin.setFailedAttempts(0);
        admin.setDeleted(0);
        userMapper.insert(admin);
        roleService.assign(admin.getId(), java.util.List.of("ADMIN", "USER"));

        log.warn("已创建初始管理员账号 username={} 角色={} —— 请登录后立即修改密码（密码来自 uc.admin.seed-password）",
                username, Role.ROLE_ADMIN);
    }
}
