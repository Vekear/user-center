-- 基础字典数据（幂等）：角色。
-- 管理员账号不在这里插入：BCrypt 哈希由应用启动时的 DataSeeder 生成，
-- 避免把「手写的哈希常量」固化进仓库。
INSERT IGNORE INTO `role` (`code`, `name`, `description`)
VALUES ('ROLE_USER', '普通用户', '注册用户默认角色'),
       ('ROLE_ADMIN', '管理员', '可进入管理后台：用户管理、审计日志、群发通知');
