package com.example.usercenter.api;

import com.example.usercenter.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证主链路 + 失败分支。
 */
class AuthApiIT extends ApiTestBase {

    @Test
    void registerLoginAndMe() throws Exception {
        String username = uniqueUsername("auth");
        register(username, username + "@example.com", PASSWORD);

        LoginResult login = login(username, PASSWORD);
        assertThat(login.accessToken()).isNotBlank();
        assertThat(login.refreshCookie()).isNotNull();
        assertThat(login.refreshCookie().isHttpOnly()).isTrue();
        assertThat(login.body().path("data").path("user").path("roles").get(0).asText()).isEqualTo("USER");

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(me).path("data").path("username").asText()).isEqualTo(username);
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String username = uniqueUsername("dup");
        register(username, username + "@example.com", PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", username,
                                "email", uniqueUsername("other") + "@example.com",
                                "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(json(result).path("code").asInt()).isEqualTo(4001);
        assertThat(json(result).path("data").path("username").asText()).contains("已存在");
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String username = uniqueUsername("dupemail");
        String email = username + "@example.com";
        register(username, email, PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", uniqueUsername("dupemail2"),
                                "email", email, "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    void weakPasswordReturns400WithFieldError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", uniqueUsername("weak"),
                                "email", uniqueUsername("weak") + "@example.com",
                                "password", "allletters"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(json(result).path("message").asText()).contains("字母和数字");
    }

    @Test
    void invalidPayloadReturns400WithFieldDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "a", "email", "not-an-email", "password", "x1"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode data = json(result).path("data");
        assertThat(data.has("username")).isTrue();
        assertThat(data.has("email")).isTrue();
    }

    @Test
    void wrongPasswordLocksAccountAfterFiveAttempts() throws Exception {
        String username = uniqueUsername("lock");
        register(username, username + "@example.com", PASSWORD);

        int lockedStatus = 0;
        JsonNode lastBody = null;
        for (int i = 1; i <= 5; i++) {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("username", username, "password", "WrongPass123"))))
                    .andReturn();
            lockedStatus = result.getResponse().getStatus();
            lastBody = json(result);
        }
        assertThat(lockedStatus).isEqualTo(403);
        assertThat(lastBody.path("code").asInt()).isEqualTo(3003);

        // 锁定期间即使密码正确也不能登录
        MvcResult correct = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(json(correct).path("code").asInt()).isEqualTo(3003);

        // 失败次数与锁定时间已落库（不被事务回滚）
        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM `user` WHERE username = ?", Integer.class, username);
        Object lockedUntil = jdbcTemplate.queryForObject(
                "SELECT locked_until FROM `user` WHERE username = ?", Object.class, username);
        assertThat(attempts).isZero();
        assertThat(lockedUntil).isNotNull();
    }

    @Test
    void refreshRotatesTokenAndLogoutKillsAccessToken() throws Exception {
        String username = uniqueUsername("refresh");
        register(username, username + "@example.com", PASSWORD);
        LoginResult login = login(username, PASSWORD);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn();
        String rotated = json(refreshed).path("data").path("accessToken").asText();
        assertThat(rotated).isNotBlank().isNotEqualTo(login.accessToken());
        Cookie rotatedCookie = refreshed.getResponse().getCookie("uc_refresh");
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(login.refreshCookie().getValue());

        // 旧 refresh token 已被轮换作废
        mockMvc.perform(post("/api/auth/refresh").cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout").cookie(rotatedCookie)).andExpect(status().isOk());
        // 会话已吊销，旧 access token 立即失效（不是等到 30 分钟过期）
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + rotated))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieReturns401() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(json(result).path("code").asInt()).isEqualTo(2001);
    }

    @Test
    void garbageTokenReturns401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(json(result).path("code").asInt()).isEqualTo(2001);
    }
}
