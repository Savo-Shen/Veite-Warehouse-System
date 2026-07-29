-- 数据库备份工具菜单（数据库对齐页面执行一次即可自动补齐）
SET NAMES utf8mb4;

DELETE FROM `sys_menu` WHERE `menu_id` = 1940000000000000011;
INSERT INTO `sys_menu` VALUES (1940000000000000011, '数据库备份', 1808758090157985794, 10, 'dbBackup', 'wms/tool/dbBackup/index', NULL, 0, 0, 'C', '1', '1', 'wms:tool:dbBackup', 'download', 'admin', NOW(), 'admin', NOW(), '数据库备份工具');
