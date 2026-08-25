-- ============================================================
-- ec_category_normalize.sql 的回滚脚本
--
-- 还原 A 部分对 wms_item_category 的 3 处改动，并拆掉 B 部分新增的表与列。
-- 幂等：UPDATE 带现值守卫，DROP 走 information_schema 判断，重复执行是空操作。
--
-- 注意：DROP COLUMN ec_category_id 与 DROP TABLE ec_category 会丢掉已回填的
--       平台类目 ID。若只想撤销 A 部分，把 B 部分那两段注释掉再执行。
-- ============================================================


-- ------------------------------------------------------------
-- 回滚 A3  恢复类目名里的「（亚德客型）」
-- ------------------------------------------------------------
UPDATE wms_item_category SET category_name = 'SC系列（亚德客型）'
 WHERE id = 1945740847748255746 AND category_name = 'SC系列标准气缸';

UPDATE wms_item_category SET category_name = 'SDA薄型气缸（亚德客型）'
 WHERE id = 1947134362210308098 AND category_name = 'SDA薄型气缸';

UPDATE wms_item_category SET category_name = 'MAL铝合金迷你气缸（亚德客型）'
 WHERE id = 1947192395711942658 AND category_name = 'MAL铝合金迷你气缸';

UPDATE wms_item_category SET category_name = 'MA系列不锈钢迷你气缸（亚德客型）'
 WHERE id = 1950745480891736066 AND category_name = 'MA系列不锈钢迷你气缸';

UPDATE wms_item_category SET category_name = 'TN双杆气缸（亚德客型）'
 WHERE id = 1951559179839741953 AND category_name = 'TN双杆气缸';


-- ------------------------------------------------------------
-- 回滚 A2  阀类 → 电磁阀
-- ------------------------------------------------------------
UPDATE wms_item_category SET category_name = '电磁阀'
 WHERE id = 1950101112237367298 AND category_name = '阀类';


-- ------------------------------------------------------------
-- 回滚 A1  轴向柱塞泵 移回一级类目
-- ------------------------------------------------------------
UPDATE wms_item_category SET parent_id = 0
 WHERE id = 2082332915953319938
   AND category_name = '轴向柱塞泵'
   AND parent_id = 2082332979916455938;


-- ------------------------------------------------------------
-- 回滚 B2  去掉 ec_category_id 列与索引
-- ------------------------------------------------------------
SET @drop_ec_idx = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME   = 'wms_item_category'
            AND INDEX_NAME   = 'idx_ec_category_id'),
  'DROP INDEX `idx_ec_category_id` ON `wms_item_category`',
  'SELECT ''idx_ec_category_id 不存在'''
);
PREPARE s FROM @drop_ec_idx; EXECUTE s; DEALLOCATE PREPARE s;

SET @drop_ec_col = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME   = 'wms_item_category'
            AND COLUMN_NAME  = 'ec_category_id'),
  'ALTER TABLE `wms_item_category` DROP COLUMN `ec_category_id`',
  'SELECT ''ec_category_id 不存在'''
);
PREPARE s FROM @drop_ec_col; EXECUTE s; DEALLOCATE PREPARE s;


-- ------------------------------------------------------------
-- 回滚 B1  删除电商类目表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ec_category`;
