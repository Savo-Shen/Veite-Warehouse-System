-- ----------------------------
-- 环境配置页面（基础资料 → 环境配置）
-- 1. 高德地图 Key / 安全密钥 存入 sys_config，前端运行时读取，改配置无需重新打包
-- 2. 新增「环境配置」菜单，并把菜单授权给已拥有「数据库对齐」的角色（仓库管理员、root）
--    菜单 perms 设为 system:config:edit，使这些角色可以调用参数保存接口
-- 脚本幂等，可重复执行。
-- ----------------------------

-- 参数项
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT 1940000000000001001, '高德地图-JS API Key', 'wms.amap.key', '', 'Y', 'admin', NOW(),
       '高德开放平台「Web端(JS API)」类型 Key，用于来往单位地图选点与分布图。申请：https://console.amap.com/dev/key/app'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'wms.amap.key');

INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT 1940000000000001002, '高德地图-安全密钥', 'wms.amap.securityCode', '', 'Y', 'admin', NOW(),
       '与 JS API Key 配套的安全密钥（jscode），在高德控制台创建 Key 时生成'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'wms.amap.securityCode');

-- 菜单：基础资料(1808758090157985794) → 环境配置
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 1940000000000000020, '环境配置', 1808758090157985794, 10, 'envConfig', 'wms/tool/envConfig/index', NULL, 1, 0, 'C', '1', '1', 'system:config:edit', 'system', 'admin', NOW(), '地图 Key 等运行时参数配置'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 1940000000000000020);

-- 角色授权：与「数据库对齐」菜单保持一致
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT rm.`role_id`, 1940000000000000020
FROM `sys_role_menu` rm
WHERE rm.`menu_id` = 1940000000000000010
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 1940000000000000020
  );
