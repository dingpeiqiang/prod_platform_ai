-- ============================================================
-- Prod Platform AI - 建库与账号（MySQL 8.0+）
-- 执行身份：具备 CREATE DATABASE / GRANT 权限的管理员
-- 用法：mysql -uroot -p < sql/00_create_database.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS `prodplatformai`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 应用账号（密码与 application.yml 默认值一致，生产请修改）
CREATE USER IF NOT EXISTS 'prodplatformai'@'%' IDENTIFIED BY 'prodplatformai@134';
CREATE USER IF NOT EXISTS 'prodplatformai'@'localhost' IDENTIFIED BY 'prodplatformai@134';

GRANT ALL PRIVILEGES ON `prodplatformai`.* TO 'prodplatformai'@'%';
GRANT ALL PRIVILEGES ON `prodplatformai`.* TO 'prodplatformai'@'localhost';

FLUSH PRIVILEGES;

USE `prodplatformai`;
