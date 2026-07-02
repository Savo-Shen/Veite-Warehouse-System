-- ----------------------------
-- 仓库位置（配合「地图总览」页面）
-- wms_warehouse 增加地址与经纬度（高德坐标系 GCJ-02）
-- 说明：「地图总览」是前端静态路由（/wms/map，位于「数据大屏」与「AI 助手」之间），
--       与首页/大屏同级，不占用数据库菜单，因此本脚本不再创建菜单。
-- 脚本幂等，可重复执行。
-- ----------------------------

SET @add_wh_address = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_warehouse'
      AND COLUMN_NAME = 'address'
  ),
  'SELECT ''wms_warehouse.address 已存在''',
  'ALTER TABLE `wms_warehouse` ADD COLUMN `address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''地址'' AFTER `warehouse_name`'
);
PREPARE add_wh_address_stmt FROM @add_wh_address;
EXECUTE add_wh_address_stmt;
DEALLOCATE PREPARE add_wh_address_stmt;

SET @add_wh_longitude = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_warehouse'
      AND COLUMN_NAME = 'longitude'
  ),
  'SELECT ''wms_warehouse.longitude 已存在''',
  'ALTER TABLE `wms_warehouse` ADD COLUMN `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT ''经度（GCJ-02）'' AFTER `address`'
);
PREPARE add_wh_longitude_stmt FROM @add_wh_longitude;
EXECUTE add_wh_longitude_stmt;
DEALLOCATE PREPARE add_wh_longitude_stmt;

SET @add_wh_latitude = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_warehouse'
      AND COLUMN_NAME = 'latitude'
  ),
  'SELECT ''wms_warehouse.latitude 已存在''',
  'ALTER TABLE `wms_warehouse` ADD COLUMN `latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT ''纬度（GCJ-02）'' AFTER `longitude`'
);
PREPARE add_wh_latitude_stmt FROM @add_wh_latitude;
EXECUTE add_wh_latitude_stmt;
DEALLOCATE PREPARE add_wh_latitude_stmt;

-- 清理：早期版本曾把「地图总览」建成基础资料下的数据库菜单，现改为前端静态路由，删除旧菜单
DELETE FROM `sys_role_menu` WHERE `menu_id` = 1940000000000000021;
DELETE FROM `sys_menu` WHERE `menu_id` = 1940000000000000021;
