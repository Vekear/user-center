package com.example.usercenter.api;

import com.example.usercenter.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台：权限边界、用户管理、重置密码、群发通知、审计可查。
 */
class AdminApiIT extends ApiTestBase {

    @Test
    void nonAdminIsForbidden() throws Exception {
        String token = registerAndLogin(uniqueUsername("nobody"));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotDisableOrReteamlSelf() throws Exception {
        String token = adminToken();
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, ADMIN_USERNAME);

        MvcResult disableSelf = mockMvc.perform(patch("/api/admin/users/" + adminId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(json(disableSelf).path("code").asInt()).isEqualTo(4092);

        mockMvc.perform(put("/api/admin/users/" + adminId + "/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"USER\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminManagesAnotherUserLifecycle() throws Exception {
        String token = adminToken();
        String username = uniqueUsername("managed");
        register(username, username + "@example.com", PASSWORD);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, username);

        // 列表 + 关键字过滤
        MvcResult page = mockMvc.perform(get("/api/admin/users")
                        .param("keyword", username).param("page", "1").param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = json(page).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(1L);
        assertThat(data.path("list").get(0).path("roles").get(0).asText()).isEqualTo("USER");

        // 停用 → 不能登录 → 恢复
        mockMvc.perform(patch("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        MvcResult disabledLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(json(disabledLogin).path("code").asInt()).isEqualTo(3002);

        mockMvc.perform(patch("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        // 角色改为 ADMIN
        MvcResult roles = mockMvc.perform(put("/api/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"USER\",\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(roles).path("data").path("roles").toString()).contains("ADMIN");

        // 新管理员确实有管理权限
        String newAdminToken = login(username, PASSWORD).accessToken();
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + newAdminToken))
                .andExpect(status().isOk());

        // 重置密码：旧密码失效、临时密码可用、全部会话被吊销
        MvcResult reset = mockMvc.perform(post("/api/admin/users/" + userId + "/reset-password")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String tempPassword = json(reset).path("data").path("tempPassword").asText();
        assertThat(tempPassword).hasSize(12);
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + newAdminToken))
                .andExpect(status().isUnauthorized());
        assertThat(login(username, PASSWORD).accessToken()).isNull();
        assertThat(login(username, tempPassword).accessToken()).isNotBlank();

        // 非法角色取值被拒
        mockMvc.perform(put("/api/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPERUSER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminStatsAndAuditLogsAreReadable() throws Exception {
        String token = adminToken();

        MvcResult stats = mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = json(stats).path("data");
        assertThat(data.path("userTotal").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(data.path("userActive").asLong()).isGreaterThanOrEqualTo(1L);

        MvcResult audits = mockMvc.perform(get("/api/admin/audit-logs")
                        .param("page", "1").param("size", "20").param("action", "ADMIN_")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(audits).path("data").path("total").asLong()).isGreaterThanOrEqualTo(0L);

        // 非法时间格式要 400，而不是被静默忽略
        MvcResult badTime = mockMvc.perform(get("/api/admin/audit-logs")
                        .param("from", "2026/10/09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(json(badTime).path("data").has("from")).isTrue();
    }

    @Test
    void broadcastNotificationReachesTargetUser() throws Exception {
        String token = adminToken();
        String username = uniqueUsername("notify");
        register(username, username + "@example.com", PASSWORD);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, username);
        String userToken = login(username, PASSWORD).accessToken();

        long before = countNotifications(userId);
        mockMvc.perform(post("/api/admin/notifications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "userIds", List.of(userId),
                                "title", "系统维护通知",
                                "content", "今晚 23:00 起维护，预计 30 分钟 🚧"))))
                .andExpect(status().isOk());

        assertThat(countNotifications(userId)).isEqualTo(before + 1);

        MvcResult unread = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(unread).path("data").path("count").asLong()).isGreaterThanOrEqualTo(1L);

        // 标记全部已读
        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        MvcResult after = mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(after).path("data").path("count").asLong()).isZero();

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'ADMIN_NOTIFICATION_SEND'", Long.class);
        assertThat(auditCount).isGreaterThanOrEqualTo(1L);
    }

    private long countNotifications(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }
}
