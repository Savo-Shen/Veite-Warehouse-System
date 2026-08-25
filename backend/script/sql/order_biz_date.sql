-- ----------------------------
-- 出入库单「业务日期」
-- 补前几天的单子时，create_time 永远是录入当天，报表就会把这单算到今天头上。
-- 加一个 biz_date 表示「这单实际发生在哪天」，列表、流水、看板统计一律按它算，
-- create_time 保留原义（什么时候录进系统的），审计时还能对得上。
-- 历史数据回填成 date(create_time)，口径不变。
-- 脚本幂等，可重复执行。
-- ----------------------------

-- wms_shipment_order.biz_date
SET @add_ship = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_shipment_order' AND COLUMN_NAME = 'biz_date'),
  'SELECT ''wms_shipment_order.biz_date 已存在''',
  'ALTER TABLE `wms_shipment_order` ADD COLUMN `biz_date` date NULL DEFAULT NULL COMMENT ''业务日期，单据实际发生的那天（补录时可选过去）'' AFTER `order_status`'
);
PREPARE s FROM @add_ship; EXECUTE s; DEALLOCATE PREPARE s;

-- wms_receipt_order.biz_date
SET @add_rec = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_receipt_order' AND COLUMN_NAME = 'biz_date'),
  'SELECT ''wms_receipt_order.biz_date 已存在''',
  'ALTER TABLE `wms_receipt_order` ADD COLUMN `biz_date` date NULL DEFAULT NULL COMMENT ''业务日期，单据实际发生的那天（补录时可选过去）'' AFTER `order_status`'
);
PREPARE s FROM @add_rec; EXECUTE s; DEALLOCATE PREPARE s;

-- wms_inventory_history.biz_date：库存流水跟着单据的业务日期走
SET @add_his = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_inventory_history' AND COLUMN_NAME = 'biz_date'),
  'SELECT ''wms_inventory_history.biz_date 已存在''',
  'ALTER TABLE `wms_inventory_history` ADD COLUMN `biz_date` date NULL DEFAULT NULL COMMENT ''业务日期，取自所属单据'' AFTER `order_type`'
);
PREPARE s FROM @add_his; EXECUTE s; DEALLOCATE PREPARE s;

-- 回填历史数据（只补空值，重复执行不会动已有值）
UPDATE `wms_shipment_order`    SET `biz_date` = DATE(`create_time`) WHERE `biz_date` IS NULL AND `create_time` IS NOT NULL;
UPDATE `wms_receipt_order`     SET `biz_date` = DATE(`create_time`) WHERE `biz_date` IS NULL AND `create_time` IS NOT NULL;
UPDATE `wms_inventory_history` SET `biz_date` = DATE(`create_time`) WHERE `biz_date` IS NULL AND `create_time` IS NOT NULL;

-- 列表和看板都要按 biz_date 过滤/分组，建索引
SET @idx_ship = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_shipment_order' AND INDEX_NAME = 'idx_biz_date'),
  'SELECT ''wms_shipment_order.idx_biz_date 已存在''',
  'ALTER TABLE `wms_shipment_order` ADD INDEX `idx_biz_date` (`biz_date`)'
);
PREPARE s FROM @idx_ship; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_rec = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_receipt_order' AND INDEX_NAME = 'idx_biz_date'),
  'SELECT ''wms_receipt_order.idx_biz_date 已存在''',
  'ALTER TABLE `wms_receipt_order` ADD INDEX `idx_biz_date` (`biz_date`)'
);
PREPARE s FROM @idx_rec; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_his = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_inventory_history' AND INDEX_NAME = 'idx_biz_date'),
  'SELECT ''wms_inventory_history.idx_biz_date 已存在''',
  'ALTER TABLE `wms_inventory_history` ADD INDEX `idx_biz_date` (`biz_date`)'
);
PREPARE s FROM @idx_his; EXECUTE s; DEALLOCATE PREPARE s;
