-- ----------------------------
-- 往来单位「物流单位」类别
-- merchant_type 字典：1 客户 / 2 供应商 / 3 物流单位
-- 原字典 3 为「客户/供应商」，无任何单位使用，按 merchant_map.sql 的设计改为「物流单位」
-- （前端地图图层、图例、编号段都已按 3 = 物流单位 实现，这里把字典补齐）
-- 脚本幂等，可重复执行。执行后请清理 Redis 中的字典缓存（DEL sys_dict）。
-- ----------------------------

-- 已存在则更新为物流单位
UPDATE `sys_dict_data`
SET `dict_label` = '物流单位',
    `list_class` = 'warning',
    `dict_sort` = 3,
    `status` = '1',
    `remark` = '物流单位'
WHERE `dict_type` = 'merchant_type' AND `dict_value` = '3';

-- 不存在则新增（老库可能没有 3 这一项）
INSERT INTO `sys_dict_data`
  (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT UNIX_TIMESTAMP() * 1000, 3, '物流单位', '3', 'merchant_type', NULL, 'warning', 'N', '1', 'admin', NOW(), '物流单位'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT * FROM `sys_dict_data`) d
  WHERE d.`dict_type` = 'merchant_type' AND d.`dict_value` = '3'
);

-- 客户 / 供应商 的标签配色
UPDATE `sys_dict_data` SET `list_class` = 'primary' WHERE `dict_type` = 'merchant_type' AND `dict_value` = '1';
UPDATE `sys_dict_data` SET `list_class` = 'success' WHERE `dict_type` = 'merchant_type' AND `dict_value` = '2';
