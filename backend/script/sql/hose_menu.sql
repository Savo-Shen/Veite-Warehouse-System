-- ----------------------------
-- 压油管菜单
--
-- 单开一个顶级目录「压油管」，不挂在「基础资料」下 —— 压油管是柜台上的一条独立业务
-- （客户报规格 → 查料 → 报价 → 压管），跟商品档案维护不是一回事。
--
-- 超级管理员(admin)自动拥有全部菜单；其它角色需在下面授权或在「角色管理」中勾选。
-- 幂等：先 DELETE 固定 menu_id 再 INSERT，重复执行结果一致。
-- 依赖：hose_module.sql（建表）、hose_module_seed.sql（种子数据）
-- ----------------------------

SET NAMES utf8mb4;

DELETE FROM `sys_menu` WHERE `menu_id` IN (
  1991000000000000001, 1991000000000000002,
  1991000000000000003, 1991000000000000004
);

-- 顶级目录
INSERT INTO `sys_menu` VALUES
(1991000000000000001, '压油管', 0, 106, 'hose', NULL, NULL, 0, 0, 'M', '1', '1', NULL, 'tool', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '液压胶管总成：配料、报价、压管');

-- 工作台
INSERT INTO `sys_menu` VALUES
(1991000000000000002, '配料与库存', 1991000000000000001, 1, 'workbench', 'wms/hose/index', NULL, 0, 0, 'C', '1', '1', 'wms:hose:list', 'form', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '输入需求出料单/位置/教程/报价，并查胶管接头外套库存');

-- 按钮权限
INSERT INTO `sys_menu` VALUES
(1991000000000000003, '压油管查询', 1991000000000000002, 1, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:hose:list', '#', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '配料查询与库存查看'),
(1991000000000000004, '压油管录入', 1991000000000000002, 2, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:hose:edit', '#', 'admin', '2026-08-23 00:00:00', 'admin', '2026-08-23 00:00:00', '盘点回填、裁管扣库存、扣压参数录入');

-- ----------------------------
-- 角色授权
--
-- 只有 admin 会自动拥有全部菜单。实际在用的账号（savo_shen / puppy / yhp）都是
-- warehouse_manager 角色，不授权就在菜单里看不到 —— 这一步不能省。
-- 授权后需要退出重新登录，或刷新页面重新拉取路由。
-- ----------------------------
INSERT IGNORE INTO `sys_role_menu` (role_id, menu_id)
SELECT r.role_id, m.menu_id
  FROM sys_role r
  CROSS JOIN (
    SELECT 1991000000000000001 AS menu_id UNION ALL
    SELECT 1991000000000000002 UNION ALL
    SELECT 1991000000000000003 UNION ALL
    SELECT 1991000000000000004
  ) m
 WHERE r.role_key = 'warehouse_manager';

SELECT menu_id, menu_name, parent_id, path, component, perms
  FROM sys_menu WHERE menu_id BETWEEN 1991000000000000001 AND 1991000000000000004
  ORDER BY menu_id;

SELECT r.role_key, COUNT(*) AS 已授权菜单数
  FROM sys_role_menu rm JOIN sys_role r ON r.role_id = rm.role_id
 WHERE rm.menu_id BETWEEN 1991000000000000001 AND 1991000000000000004
 GROUP BY r.role_key;
