package com.example.usercenter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目自身的可调参数。所有对外可见的行为（令牌时长、锁定策略、头像限制、种子管理员）都在这里，
 * 便于部署时用环境变量覆盖而不改代码。
 */
@Data
@ConfigurationProperties(prefix = "uc")
public class UcProperties {

    private Jwt jwt = new Jwt();
    private Cookie cookie = new Cookie();
    private Cors cors = new Cors();
    private Avatar avatar = new Avatar();
    private Login login = new Login();
    private Admin admin = new Admin();

    @Data
    public static class Jwt {
        private String secret;
        private String issuer = "user-center";
        private int accessTokenMinutes = 30;
        private int refreshTokenDays = 14;
    }

    @Data
    public static class Cookie {
        private String refreshName = "uc_refresh";
        private boolean secure = false;
        private String sameSite = "Lax";
        private String path = "/api/auth";
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    @Data
    public static class Avatar {
        private String dir = "./data/avatars";
        private String publicPrefix = "/api/files/avatars";
        private long maxBytes = 2 * 1024 * 1024L;
        private int maxDimension = 4096;
        private int minDimension = 16;
    }

    @Data
    public static class Login {
        private int maxFailedAttempts = 5;
        private int lockMinutes = 10;
    }

    @Data
    public static class Admin {
        private String seedUsername = "admin";
        private String seedPassword;
        private String seedEmail = "admin@example.com";
        private String seedNickname = "系统管理员";
    }
}
