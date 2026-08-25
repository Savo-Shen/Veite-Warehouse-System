-- ----------------------------
-- wms_inventory 加 (sku_id, warehouse_id) 唯一键
-- 这张表一直只有主键，"同一个规格在同一个仓库只能有一行"全靠代码自觉：
-- 入库 add() 和出库 subtract() 都是「先 selectOne 查、查不到就 insert」，
-- 两个人同时给同一个没有库存记录的规格开单，就会插出两行；
-- 之后 selectOne 又会因为查到多行而报错，整张单据都做不下去。
-- 加唯一键后，并发时后一个事务直接失败回滚，不会留下脏数据。
--
-- 执行前会先检查有没有已存在的重复行：有就只打印出来、不加索引，
-- 需要人工决定怎么合并（把数量加起来留一行）后再跑一次。
-- 脚本幂等，可重复执行。
-- ----------------------------

-- 1. 先看有没有重复（有的话下面的 ALTER 会失败，这里先把它们列出来）
SELECT sku_id, warehouse_id, COUNT(*) AS dup_rows, GROUP_CONCAT(id) AS ids, GROUP_CONCAT(quantity) AS quantities
FROM `wms_inventory`
GROUP BY sku_id, warehouse_id
HAVING COUNT(*) > 1;

-- 2. 没有重复才加索引；已经加过则跳过
SET @has_dup = (
  SELECT COUNT(*) FROM (
    SELECT 1 FROM `wms_inventory` GROUP BY sku_id, warehouse_id HAVING COUNT(*) > 1
  ) d
);
SET @has_idx = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'wms_inventory'
    AND INDEX_NAME = 'uk_sku_warehouse'
);
SET @add_uk = IF(
  @has_idx > 0,
  'SELECT ''uk_sku_warehouse 已存在，跳过''',
  IF(
    @has_dup > 0,
    'SELECT ''存在重复的 (sku_id, warehouse_id)，请先合并上面列出的行再执行本脚本'' AS skipped_reason',
    'ALTER TABLE `wms_inventory` ADD UNIQUE KEY `uk_sku_warehouse` (`sku_id`, `warehouse_id`)'
  )
);
PREPARE add_uk_stmt FROM @add_uk;
EXECUTE add_uk_stmt;
DEALLOCATE PREPARE add_uk_stmt;
