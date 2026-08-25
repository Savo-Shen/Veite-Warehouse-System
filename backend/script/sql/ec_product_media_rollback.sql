-- ============================================================
-- ec_product_media.sql 的回滚脚本
--
-- 注意：会丢掉 wms_item_media 里已登记的素材路径、ec_product 里已写的标题与卖点，
--       以及 wms_item_sku 已填的包装尺寸。执行前确认这些数据不需要保留。
-- 幂等：DROP 走 IF EXISTS / information_schema 判断。
-- ============================================================

DROP TABLE IF EXISTS `ec_product_item`;
DROP TABLE IF EXISTS `ec_product`;
DROP TABLE IF EXISTS `wms_item_media`;

SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_item_sku'
                      AND COLUMN_NAME='pack_height'),
  'ALTER TABLE `wms_item_sku` DROP COLUMN `pack_height`',
  'SELECT ''pack_height 不存在''');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_item_sku'
                      AND COLUMN_NAME='pack_width'),
  'ALTER TABLE `wms_item_sku` DROP COLUMN `pack_width`',
  'SELECT ''pack_width 不存在''');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_item_sku'
                      AND COLUMN_NAME='pack_length'),
  'ALTER TABLE `wms_item_sku` DROP COLUMN `pack_length`',
  'SELECT ''pack_length 不存在''');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;
