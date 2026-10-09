package com.example.usercenter.api;

import com.example.usercenter.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资料、头像、账号安全（含 utf8mb4 与"改密码踢其他设备"这两条容易出错的路径）。
 */
class ProfileApiIT extends ApiTestBase {

    private byte[] png(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.decode("#2F5D62"));
        graphics.fillRect(0, 0, size, size);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void updateProfilePersistsEmojiAndNullableFields() throws Exception {
        String username = uniqueUsername("profile");
        String token = registerAndLogin(username);

        MvcResult updated = mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "nickname", "档案册·阿离🚀",
                                "email", username + "@example.com",
                                "phone", "13900002222",
                                "gender", "FEMALE",
                                "birthday", "1996-03-18",
                                "bio", "喜欢在深夜写代码 ✨"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = json(updated).path("data");
        assertThat(data.path("nickname").asText()).isEqualTo("档案册·阿离🚀");
        assertThat(data.path("bio").asText()).isEqualTo("喜欢在深夜写代码 ✨");

        // 库里也必须是同一个字符串（utf8mb4 全链路无损坏）
        String stored = jdbcTemplate.queryForObject(
                "SELECT nickname FROM `user` WHERE username = ?", String.class, username);
        assertThat(stored).isEqualTo("档案册·阿离🚀");

        // 传空字符串等于清空
        MvcResult cleared = mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("nickname", username, "email", username + "@example.com",
                                "phone", "", "gender", "", "bio", ""))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(cleared).path("data").path("phone").asText("")).isEmpty();
        assertThat(json(cleared).path("data").path("bio").asText("")).isEmpty();
        assertThat(json(cleared).path("data").path("gender").asText("")).isEmpty();
    }

    @Test
    void duplicateEmailOnUpdateReturns409() throws Exception {
        String first = uniqueUsername("emailA");
        String second = uniqueUsername("emailB");
        registerAndLogin(first);
        String token = registerAndLogin(second);

        MvcResult result = mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("nickname", second, "email", first + "@example.com"))))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(json(result).path("code").asInt()).isEqualTo(4002);
    }

    @Test
    void changePasswordKeepsCurrentSessionAndRevokesOthers() throws Exception {
        String username = uniqueUsername("pwd");
        register(username, username + "@example.com", PASSWORD);
        LoginResult deviceA = login(username, PASSWORD);
        LoginResult deviceB = login(username, PASSWORD);

        // B 能正常访问
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + deviceB.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/profile/password")
                        .header("Authorization", "Bearer " + deviceA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("oldPassword", PASSWORD, "newPassword", "NewPassw0rd456"))))
                .andExpect(status().isOk());

        // 当前设备继续可用，其他设备立即失效
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + deviceA.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + deviceB.accessToken()))
                .andExpect(status().isUnauthorized());

        // 旧密码失效、新密码可用
        assertThat(login(username, PASSWORD).accessToken()).isNull();
        assertThat(login(username, "NewPassw0rd456").accessToken()).isNotBlank();

        // 留下一条安全通知
        long securityNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification n JOIN `user` u ON u.id = n.user_id "
                        + "WHERE u.username = ? AND n.type = 'SECURITY'", Long.class, username);
        assertThat(securityNotifications).isEqualTo(1L);
    }

    @Test
    void changePasswordWithWrongOldPasswordReturns400() throws Exception {
        String username = uniqueUsername("pwdwrong");
        String token = registerAndLogin(username);

        MvcResult result = mockMvc.perform(put("/api/profile/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("oldPassword", "WrongOld123", "newPassword", "NewPassw0rd456"))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(json(result).path("code").asInt()).isEqualTo(4005);

        // 失败审计必须落库
        long failedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log a JOIN `user` u ON u.id = a.user_id "
                        + "WHERE u.username = ? AND a.action = 'PASSWORD_CHANGE' AND a.success = 0",
                Long.class, username);
        assertThat(failedAudits).isEqualTo(1L);
    }

    @Test
    void avatarUploadThenDelete() throws Exception {
        String username = uniqueUsername("avatar");
        String token = registerAndLogin(username);

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", png(64));
        MvcResult uploaded = mockMvc.perform(multipart("/api/profile/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String url = json(uploaded).path("data").path("avatarUrl").asText();
        assertThat(url).startsWith("/api/files/avatars/").endsWith(".png");

        String stored = jdbcTemplate.queryForObject(
                "SELECT avatar_url FROM `user` WHERE username = ?", String.class, username);
        assertThat(stored).isEqualTo(url);

        mockMvc.perform(delete("/api/profile/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT avatar_url FROM `user` WHERE username = ?", String.class, username)).isNull();
    }

    @Test
    void avatarUploadRejectsNonImage() throws Exception {
        String token = registerAndLogin(uniqueUsername("badfile"));

        MockMultipartFile file = new MockMultipartFile("file", "evil.png", "image/png",
                "<?php echo 'pwned'; ?>".getBytes());
        MvcResult result = mockMvc.perform(multipart("/api/profile/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(json(result).path("code").asInt()).isEqualTo(5001);
    }

    @Test
    void sessionsListMarksCurrentAndCanRevokeOthers() throws Exception {
        String username = uniqueUsername("sessions");
        register(username, username + "@example.com", PASSWORD);
        LoginResult deviceA = login(username, PASSWORD);
        LoginResult deviceB = login(username, PASSWORD);

        MvcResult list = mockMvc.perform(get("/api/security/sessions")
                        .header("Authorization", "Bearer " + deviceA.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sessions = json(list).path("data");
        assertThat(sessions.size()).isEqualTo(2);
        assertThat(sessions.get(0).path("current").asBoolean()
                || sessions.get(1).path("current").asBoolean()).isTrue();

        MvcResult revoked = mockMvc.perform(delete("/api/security/sessions/others")
                        .header("Authorization", "Bearer " + deviceA.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(revoked).path("data").asInt()).isEqualTo(1);

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + deviceB.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + deviceA.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void cannotRevokeSomeoneElsesSession() throws Exception {
        String victim = uniqueUsername("victim");
        String attacker = uniqueUsername("attacker");
        register(victim, victim + "@example.com", PASSWORD);
        LoginResult victimLogin = login(victim, PASSWORD);
        String attackerToken = registerAndLogin(attacker);

        Long victimId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, victim);
        Long victimSessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM user_session WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, victimId);

        mockMvc.perform(delete("/api/security/sessions/" + victimSessionId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        // 受害者的会话没有被动过
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + victimLogin.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccountRequiresPasswordAndDisablesLogin() throws Exception {
        String username = uniqueUsername("goodbye");
        register(username, username + "@example.com", PASSWORD);
        LoginResult login = login(username, PASSWORD);

        mockMvc.perform(delete("/api/security/account")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("password", "WrongPass123"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/security/account")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("password", PASSWORD))))
                .andExpect(status().isOk());

        assertThat(login(username, PASSWORD).accessToken()).isNull();
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM `user` WHERE username = ?", Integer.class, username);
        assertThat(deleted).isEqualTo(1);
        // access token 也随会话一起失效
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized());
    }
}
