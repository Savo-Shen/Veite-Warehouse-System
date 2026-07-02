-- ----------------------------
-- 往来单位地图与图片支持
-- 1. wms_merchant 增加经纬度（高德坐标系 GCJ-02）与图片字段
-- 2. merchant_type 字典调整为：1 客户 / 2 供应商 / 3 物流单位，并配置标签颜色
-- 图片存储在 sys_oss，这里保存逗号分隔的 OSS ID。
-- 脚本幂等，可重复执行。执行后请清理 Redis 中的 sys_dict 缓存（DEL sys_dict）。
-- ----------------------------

SET @add_longitude = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_merchant'
      AND COLUMN_NAME = 'longitude'
  ),
  'SELECT ''wms_merchant.longitude 已存在''',
  'ALTER TABLE `wms_merchant` ADD COLUMN `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT ''经度（GCJ-02）'' AFTER `address`'
);
PREPARE add_longitude_stmt FROM @add_longitude;
EXECUTE add_longitude_stmt;
DEALLOCATE PREPARE add_longitude_stmt;

SET @add_latitude = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_merchant'
      AND COLUMN_NAME = 'latitude'
  ),
  'SELECT ''wms_merchant.latitude 已存在''',
  'ALTER TABLE `wms_merchant` ADD COLUMN `latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT ''纬度（GCJ-02）'' AFTER `longitude`'
);
PREPARE add_latitude_stmt FROM @add_latitude;
EXECUTE add_latitude_stmt;
DEALLOCATE PREPARE add_latitude_stmt;

SET @add_image_ids = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_merchant'
      AND COLUMN_NAME = 'image_ids'
  ),
  'SELECT ''wms_merchant.image_ids 已存在''',
  'ALTER TABLE `wms_merchant` ADD COLUMN `image_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''单位图片OSS ID，多个用逗号分隔'' AFTER `remark`'
);
PREPARE add_image_ids_stmt FROM @add_image_ids;
EXECUTE add_image_ids_stmt;
DEALLOCATE PREPARE add_image_ids_stmt;

-- 字典：3 原为「客户/供应商」（无数据使用），改为「物流单位」
UPDATE `sys_dict_data`
SET `dict_label` = '物流单位', `list_class` = 'warning', `remark` = '物流单位'
WHERE `dict_type` = 'merchant_type' AND `dict_value` = '3';

UPDATE `sys_dict_data`
SET `list_class` = 'primary'
WHERE `dict_type` = 'merchant_type' AND `dict_value` = '1';

UPDATE `sys_dict_data`
SET `list_class` = 'success'
WHERE `dict_type` = 'merchant_type' AND `dict_value` = '2';
