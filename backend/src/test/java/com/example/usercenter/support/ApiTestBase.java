package com.example.usercenter.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 集成测试基类：真连 MySQL 的 user_center_test 库，走完整 Security 过滤器链。
 * 每个测试用唯一用户名，避免历史数据互相干扰；不依赖执行顺序。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {

    public static final String PASSWORD = "Passw0rd123";
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String uniqueUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    protected String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** 注册一个普通用户并登录，返回 access token。 */
    protected String registerAndLogin(String username) throws Exception {
        register(username, username + "@example.com", PASSWORD);
        return login(username, PASSWORD).accessToken();
    }

    protected void register(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(java.util.Map.of(
                                "username", username, "email", email, "password", password))))
                .andReturn();
    }

    public record LoginResult(String accessToken, Cookie refreshCookie, JsonNode body) {
    }

    protected LoginResult login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(java.util.Map.of("username", username, "password", password))))
                .andReturn();
        JsonNode body = json(result);
        String token = body.path("data").path("accessToken").isMissingNode()
                ? null
                : body.path("data").path("accessToken").asText();
        return new LoginResult(token, result.getResponse().getCookie("uc_refresh"), body);
    }

    /** 保证种子管理员存在（DataSeeder 在测试上下文里同样会执行，这里只是兜底）。 */
    protected void ensureAdmin() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE username = ? AND deleted = 0", Integer.class, ADMIN_USERNAME);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO `user` (username, email, password_hash, nickname, status, failed_attempts, deleted)
                        VALUES (?, ?, ?, ?, 'ACTIVE', 0, 0)
                        """,
                ADMIN_USERNAME, "admin@example.com", passwordEncoder.encode(ADMIN_PASSWORD), "系统管理员");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, ADMIN_USERNAME);
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM role WHERE code = 'ROLE_ADMIN'", Long.class);
        jdbcTemplate.update("INSERT IGNORE INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    protected String adminToken() throws Exception {
        ensureAdmin();
        LoginResult result = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        if (result.accessToken() == null) {
            throw new IllegalStateException("管理员登录失败: " + result.body());
        }
        return result.accessToken();
    }
}
