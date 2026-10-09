-- ============================================================
-- 用户中心：数据库与最小权限账号初始化
-- 用法（需要 MySQL root 密码）：
--   mysql --host=localhost --port=3306 --user=root --password < scripts/db-init.sql
-- 说明：
--   * 只新建 user_center / user_center_test 两个库与 uc_app 账号，
--     不触碰机器上已有的任何数据库。
--   * 表结构不在这里建：后端启动时由 spring.sql.init 执行
--     resources/db/schema.sql + seed.sql（幂等），保证「代码即结构」。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `user_center`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS `user_center_test`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'uc_app'@'localhost' IDENTIFIED BY 'Uc_app_2026!';
CREATE USER IF NOT EXISTS 'uc_app'@'127.0.0.1' IDENTIFIED BY 'Uc_app_2026!';
ALTER USER 'uc_app'@'localhost' IDENTIFIED BY 'Uc_app_2026!';
ALTER USER 'uc_app'@'127.0.0.1' IDENTIFIED BY 'Uc_app_2026!';

GRANT ALL PRIVILEGES ON `user_center`.*      TO 'uc_app'@'localhost';
GRANT ALL PRIVILEGES ON `user_center`.*      TO 'uc_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `user_center_test`.* TO 'uc_app'@'localhost';
GRANT ALL PRIVILEGES ON `user_center_test`.* TO 'uc_app'@'127.0.0.1';

FLUSH PRIVILEGES;

SELECT SCHEMA_NAME AS created_schema
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME IN ('user_center', 'user_center_test');

SELECT user, host FROM mysql.user WHERE user = 'uc_app';
