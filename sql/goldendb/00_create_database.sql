-- ============================================================
-- Prod Platform AI - 建库与账号（GoldenDB 兼容版）
-- 来源：sql/mysql/00_create_database.sql 的 GoldenDB 适配
-- 注意：GoldenDB 部分部署由 DBA 统一建库建账号（库为分布式实例），
--       若代理层不支持 CREATE USER/GRANT 语法，请交由 DBA 执行本文件。
-- 用法：mysql -uroot -p < sql/goldendb/00_create_database.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS `prodplatformai`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

-- 应用账号（密码与 application.yml 默认值一致，生产请修改）
CREATE USER IF NOT EXISTS 'prodplatformai'@'%' IDENTIFIED BY 'prodplatformai@134';
CREATE USER IF NOT EXISTS 'prodplatformai'@'localhost' IDENTIFIED BY 'prodplatformai@134';

GRANT ALL PRIVILEGES ON `prodplatformai`.* TO 'prodplatformai'@'%';
GRANT ALL PRIVILEGES ON `prodplatformai`.* TO 'prodplatformai'@'localhost';

FLUSH PRIVILEGES;

USE `prodplatformai`;
