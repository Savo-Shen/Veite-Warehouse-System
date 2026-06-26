-- ----------------------------
-- 出入库单补充图片
-- 每张图片存储在 sys_oss，这里保存逗号分隔的 OSS ID。
-- 执行一次即可。
-- ----------------------------

SET @add_receipt_images = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_receipt_order'
      AND COLUMN_NAME = 'supplement_image_ids'
  ),
  'SELECT ''wms_receipt_order.supplement_image_ids 已存在''',
  'ALTER TABLE `wms_receipt_order` ADD COLUMN `supplement_image_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''补充图片OSS ID，多个用逗号分隔'' AFTER `remark`'
);
PREPARE add_receipt_images_stmt FROM @add_receipt_images;
EXECUTE add_receipt_images_stmt;
DEALLOCATE PREPARE add_receipt_images_stmt;

SET @add_shipment_images = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_shipment_order'
      AND COLUMN_NAME = 'supplement_image_ids'
  ),
  'SELECT ''wms_shipment_order.supplement_image_ids 已存在''',
  'ALTER TABLE `wms_shipment_order` ADD COLUMN `supplement_image_ids` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''补充图片OSS ID，多个用逗号分隔'' AFTER `remark`'
);
PREPARE add_shipment_images_stmt FROM @add_shipment_images;
EXECUTE add_shipment_images_stmt;
DEALLOCATE PREPARE add_shipment_images_stmt;
