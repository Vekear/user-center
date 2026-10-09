-- ============================================================
-- 用户中心表结构（幂等：可重复执行）
-- 由 spring.sql.init 在每次启动时执行，schema 全为 IF NOT EXISTS，
-- 因此「代码即结构」，无需手工迁移脚本。
-- ============================================================

CREATE TABLE IF NOT EXISTS `user`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `username`        VARCHAR(32)     NOT NULL COMMENT '登录名',
    `email`           VARCHAR(128)    NOT NULL COMMENT '邮箱',
    `phone`           VARCHAR(20)              DEFAULT NULL COMMENT '手机号',
    `password_hash`   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希',
    `nickname`        VARCHAR(64)     NOT NULL COMMENT '昵称',
    `avatar_url`      VARCHAR(255)             DEFAULT NULL COMMENT '头像访问路径',
    `gender`          VARCHAR(16)              DEFAULT NULL COMMENT 'MALE/FEMALE/UNKNOWN',
    `birthday`        DATE                     DEFAULT NULL,
    `bio`             VARCHAR(500)             DEFAULT NULL COMMENT '个人简介',
    `status`          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    `failed_attempts` INT             NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `locked_until`    DATETIME                 DEFAULT NULL COMMENT '锁定截止时间',
    `last_login_at`   DATETIME                 DEFAULT NULL,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    KEY `idx_user_status_created` (`status`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';

CREATE TABLE IF NOT EXISTS `role`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(32)     NOT NULL COMMENT 'ROLE_USER / ROLE_ADMIN',
    `name`        VARCHAR(64)     NOT NULL,
    `description` VARCHAR(255)             DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色';

CREATE TABLE IF NOT EXISTS `user_role`
(
    `user_id` BIGINT UNSIGNED NOT NULL,
    `role_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_user_role_role` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户-角色';

CREATE TABLE IF NOT EXISTS `user_session`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`            BIGINT UNSIGNED NOT NULL,
    `refresh_token_hash` CHAR(64)        NOT NULL COMMENT 'refresh token 的 SHA-256，不存明文',
    `device_name`        VARCHAR(64)              DEFAULT NULL,
    `user_agent`         VARCHAR(255)             DEFAULT NULL,
    `ip`                 VARCHAR(45)              DEFAULT NULL,
    `last_seen_at`       DATETIME                 DEFAULT NULL,
    `expires_at`         DATETIME        NOT NULL,
    `revoked`            TINYINT         NOT NULL DEFAULT 0,
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_hash` (`refresh_token_hash`),
    KEY `idx_session_user` (`user_id`, `revoked`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录会话/设备';

CREATE TABLE IF NOT EXISTS `notification`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `title`      VARCHAR(128)    NOT NULL,
    `content`    VARCHAR(1000)   NOT NULL,
    `type`       VARCHAR(32)     NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/ACCOUNT/SECURITY',
    `read_at`    DATETIME                 DEFAULT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`, `read_at`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='站内通知';

CREATE TABLE IF NOT EXISTS `audit_log`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED          DEFAULT NULL,
    `username`    VARCHAR(32)              DEFAULT NULL,
    `action`      VARCHAR(64)     NOT NULL COMMENT '动作码，如 AUTH_LOGIN',
    `target_type` VARCHAR(32)              DEFAULT NULL,
    `target_id`   VARCHAR(64)              DEFAULT NULL,
    `ip`          VARCHAR(45)              DEFAULT NULL,
    `user_agent`  VARCHAR(255)             DEFAULT NULL,
    `success`     TINYINT         NOT NULL DEFAULT 1,
    `detail`      VARCHAR(2000)            DEFAULT NULL COMMENT '已截断，禁止写入密码/令牌',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_user_time` (`user_id`, `created_at`),
    KEY `idx_audit_action_time` (`action`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='操作审计日志';
