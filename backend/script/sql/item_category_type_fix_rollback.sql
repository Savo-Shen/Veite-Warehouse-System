-- ============================================================
-- item_category_type_fix.sql 的回滚脚本
-- 把 wms_item.item_category 从 bigint 改回 varchar(20)。
-- 幂等：已是 varchar 则跳过。
-- 注意：改回 varchar 会重新引入 double 精度碰撞缺陷，仅用于应急回退。
-- ============================================================

SET @back = IF(
  (SELECT DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'wms_item'
      AND COLUMN_NAME  = 'item_category') = 'varchar',
  'SELECT ''wms_item.item_category 已是 varchar''',
  'ALTER TABLE `wms_item` MODIFY COLUMN `item_category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL'
);
PREPARE s FROM @back; EXECUTE s; DEALLOCATE PREPARE s;

SELECT DATA_TYPE, COLUMN_TYPE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'wms_item'
   AND COLUMN_NAME  = 'item_category';
