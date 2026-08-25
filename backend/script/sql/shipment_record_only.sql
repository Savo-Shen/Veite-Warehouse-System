-- ----------------------------
-- 出库单「纯记录单」
--
-- 有些生意不需要记库存（代客订货、厂家直发、临时代买），但仍然想把这笔交易电子化，
-- 方便以后回来查当时的成本和卖价。为此在出库单上加一个开关 record_only：
--   record_only = 1 → 这单只留价格备查，不扣库存、不写库存流水，商品名手工输入不挂 SKU
--   record_only = 0 → 现有的正常出库单，行为完全不变
-- 单号序列、列表、搜索都和正常出库单共用，前端靠黄色主题区分。
--
-- 同时补上明细的两个价格列。此前单价没有落库，回显时靠 amount/quantity 反推，
-- 数量为 0 或金额为空就推不出来；现在直接存。
--   sale_price → 销售价。正常单就是原来那个「单价」，纯记录单是卖出价。
--   cost_price → 成本价。两种单都可选填，用来算毛利。
--   item_name  → 纯记录单手工输入的商品名（不挂 SKU 时 sku_id 为空，靠这列显示）。
--
-- 历史数据：sale_price 从 amount/quantity 回填，口径和原来前端反推的一致。
-- 脚本幂等，可重复执行。
-- ----------------------------

-- wms_shipment_order.record_only
SET @add_flag = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_shipment_order' AND COLUMN_NAME = 'record_only'),
  'SELECT ''wms_shipment_order.record_only 已存在''',
  'ALTER TABLE `wms_shipment_order` ADD COLUMN `record_only` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''纯记录单：只留价格备查，不扣库存'' AFTER `opt_type`'
);
PREPARE s FROM @add_flag; EXECUTE s; DEALLOCATE PREPARE s;

-- wms_shipment_order_detail.item_name
SET @add_name = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_shipment_order_detail' AND COLUMN_NAME = 'item_name'),
  'SELECT ''wms_shipment_order_detail.item_name 已存在''',
  'ALTER TABLE `wms_shipment_order_detail` ADD COLUMN `item_name` varchar(255) NULL DEFAULT NULL COMMENT ''商品名称，纯记录单手工输入（不挂 SKU）'' AFTER `sku_id`'
);
PREPARE s FROM @add_name; EXECUTE s; DEALLOCATE PREPARE s;

-- wms_shipment_order_detail.cost_price
SET @add_cost = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_shipment_order_detail' AND COLUMN_NAME = 'cost_price'),
  'SELECT ''wms_shipment_order_detail.cost_price 已存在''',
  'ALTER TABLE `wms_shipment_order_detail` ADD COLUMN `cost_price` decimal(12, 2) NULL DEFAULT NULL COMMENT ''成本价'' AFTER `quantity`'
);
PREPARE s FROM @add_cost; EXECUTE s; DEALLOCATE PREPARE s;

-- wms_shipment_order_detail.sale_price
SET @add_sale = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_shipment_order_detail' AND COLUMN_NAME = 'sale_price'),
  'SELECT ''wms_shipment_order_detail.sale_price 已存在''',
  'ALTER TABLE `wms_shipment_order_detail` ADD COLUMN `sale_price` decimal(12, 2) NULL DEFAULT NULL COMMENT ''销售价（正常单即单价）'' AFTER `cost_price`'
);
PREPARE s FROM @add_sale; EXECUTE s; DEALLOCATE PREPARE s;

-- 回填历史单价：只补空值，重复执行不会覆盖已填的
UPDATE `wms_shipment_order_detail`
   SET `sale_price` = ROUND(`amount` / `quantity`, 2)
 WHERE `sale_price` IS NULL
   AND `amount` IS NOT NULL
   AND `quantity` IS NOT NULL
   AND `quantity` > 0;

-- 列表默认按创建时间倒序翻页，纯记录单要能单独筛出来
SET @idx_flag = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_shipment_order' AND INDEX_NAME = 'idx_record_only'),
  'SELECT ''wms_shipment_order.idx_record_only 已存在''',
  'ALTER TABLE `wms_shipment_order` ADD INDEX `idx_record_only` (`record_only`)'
);
PREPARE s FROM @idx_flag; EXECUTE s; DEALLOCATE PREPARE s;
