-- ----------------------------
-- 电商上新菜单
--
-- 单独建一个顶级目录「电商上新」，不挂在「基础资料」下——电商上新是一条独立的业务线，
-- 后续还会有素材库、平台映射等页面，放基础资料里会越挤越乱。
--
-- 超级管理员(admin)自动拥有全部菜单；其它角色需在「角色管理」中授权。
-- 幂等：先 DELETE 固定 menu_id 再 INSERT，重复执行结果一致。
-- ----------------------------

SET NAMES utf8mb4;

DELETE FROM `sys_menu` WHERE `menu_id` IN (
  1990000000000000001, 1990000000000000002,
  1990000000000000003, 1990000000000000004, 1990000000000000005
);

-- 顶级目录
INSERT INTO `sys_menu` VALUES
(1990000000000000001, '电商上新', 0, 105, 'ec', NULL, NULL, 0, 0, 'M', '1', '1', NULL, 'shopping', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '电商上架准备');

-- 上新工作台
INSERT INTO `sys_menu` VALUES
(1990000000000000002, '上新工作台', 1990000000000000001, 1, 'listing', 'wms/ec/listing/index', NULL, 0, 0, 'C', '1', '1', 'wms:ecProduct:list', 'form', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '按电商商品聚合的上新对照与录入');

-- 按钮权限
INSERT INTO `sys_menu` VALUES
(1990000000000000003, '上新查询', 1990000000000000002, 1, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:ecProduct:list', '#', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', ''),
(1990000000000000004, '上新编辑', 1990000000000000002, 2, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:ecProduct:edit', '#', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '保存标题/录入重量/标记状态'),
(1990000000000000005, 'AI生成标题', 1990000000000000002, 3, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:ecProduct:ai', '#', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '调用 AI 网关生成标题与卖点，会产生调用费用，建议单独授权');

-- ----------------------------
-- 角色授权
--
-- 只有 admin 会自动拥有全部菜单。实际使用的账号（savo_shen / puppy / yhp）都是
-- warehouse_manager 角色，不授权就在菜单里看不到——这一步不能省。
-- 授权后需要退出重新登录，或刷新页面重新拉取路由。
--
-- 只授给 warehouse_manager：AI 生成标题会产生网关调用费用，不该给 visitor/test 这类角色。
-- 要给别的角色开，在「角色管理」里勾，或把下面的 role_key 条件改掉。
-- ----------------------------
INSERT IGNORE INTO `sys_role_menu` (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  CROSS JOIN (
    SELECT 1990000000000000001 AS menu_id UNION ALL
    SELECT 1990000000000000002 UNION ALL
    SELECT 1990000000000000003 UNION ALL
    SELECT 1990000000000000004 UNION ALL
    SELECT 1990000000000000005
  ) m
 WHERE r.role_key = 'warehouse_manager';   -- 实际在用的角色；visitor/test 等不授予（AI 会产生费用）

SELECT menu_id, menu_name, parent_id, path, component, perms
  FROM sys_menu WHERE menu_id BETWEEN 1990000000000000001 AND 1990000000000000005
  ORDER BY menu_id;

SELECT r.role_key, COUNT(*) AS 已授权菜单数
  FROM sys_role_menu rm JOIN sys_role r ON r.role_id = rm.role_id
 WHERE rm.menu_id BETWEEN 1990000000000000001 AND 1990000000000000005
 GROUP BY r.role_key;
