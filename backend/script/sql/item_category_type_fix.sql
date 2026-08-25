-- ============================================================
-- 修复 wms_item.item_category 的类型不匹配
--
-- 问题：wms_item.item_category 是 varchar(20)，wms_item_category.id 是 bigint。
--   两者比较时 MySQL 把两边都转成 DOUBLE，19 位雪花 ID 超出 double 的 53 位尾数
--   精度，只差 1 的两个 ID 会被判定为相等。库中已存在一对相撞：
--     1946489713837772801  气动接头
--     1946489713837772802  DPOC圆螺纹直通
--   验证：SELECT '1946489713837772801'+0 = '1946489713837772802'+0;  -- 返回 1
--
-- 影响：ItemSkuMapper.xml 的
--     inner join wms_item_category category on item.item_category=category.id
--   是否出错取决于查询计划。实测当前 INNER JOIN 计划下结果正确（2279 行），
--   但同一份数据改成 LEFT JOIN 就会多出 35 行。属于潜伏缺陷，随查询计划变化
--   或新增一对相邻 ID 即可能浮现为商品列表重复行 + 分页总数错误。
--
-- 前置检查：239 行 item_category 全部为纯数字、最长 19 位、无 NULL，无索引、无外键。
-- Java 侧 Item.itemCategory 是 String，MyBatis 读 BIGINT 列转 String 照常工作，
-- 无需改动代码。
--
-- 幂等：走 information_schema 判断当前类型，已是 bigint 则跳过。
-- 回滚：执行同目录 item_category_type_fix_rollback.sql
-- 备份：backups/LOCAL_ry-vue_pre-schema-fix_20260823_145546.sql
-- ============================================================


-- ------------------------------------------------------------
-- 安全闸：若存在非纯数字值，直接报错中止，不做转换
-- ------------------------------------------------------------
SET @bad = (SELECT COUNT(*) FROM wms_item
             WHERE item_category IS NOT NULL
               AND item_category NOT REGEXP '^[0-9]+$');

SET @guard = IF(@bad > 0,
  'SELECT fail_item_category_has_non_numeric_values()',
  'SELECT ''前置检查通过：item_category 全部为纯数字''');
PREPARE s FROM @guard; EXECUTE s; DEALLOCATE PREPARE s;


-- ------------------------------------------------------------
-- varchar(20) → bigint
-- ------------------------------------------------------------
SET @fix = IF(
  (SELECT DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'wms_item'
      AND COLUMN_NAME  = 'item_category') = 'bigint',
  'SELECT ''wms_item.item_category 已是 bigint''',
  'ALTER TABLE `wms_item` MODIFY COLUMN `item_category` bigint NULL DEFAULT NULL COMMENT ''物料类型ID，关联 wms_item_category.id'''
);
PREPARE s FROM @fix; EXECUTE s; DEALLOCATE PREPARE s;


-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
SELECT '当前类型' AS check_name, DATA_TYPE, COLUMN_TYPE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'wms_item'
   AND COLUMN_NAME  = 'item_category';

-- 每个商品必须恰好匹配 1 个类目行，下面这条应返回 0 行
SELECT '匹配到多个类目的商品（应为空）' AS check_name, i.id, i.item_name, COUNT(*) AS n
  FROM wms_item i
  JOIN wms_item_category c ON c.id = i.item_category
 GROUP BY i.id, i.item_name
HAVING COUNT(*) > 1;

-- 应等于 wms_item_sku 中 item_id 有效的行数
SELECT '主查询行数' AS check_name, COUNT(*) AS rows_returned
  FROM wms_item_sku sku
  JOIN wms_item item ON sku.item_id = item.id
  JOIN wms_item_category category ON item.item_category = category.id;
