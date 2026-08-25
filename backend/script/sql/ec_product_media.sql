-- ============================================================
-- 电商上架准备：包装尺寸列 + 素材表 + 电商商品档案
--
-- ① wms_item_sku 增加包装尺寸三列。
--    原 length/width/height 语义澄清为「商品规格参数」——PU管 SKU 的 length=200
--    是 200 米盘长，不是包装尺寸。两种语义此前混在同一组列里，导出给平台会出错。
--
-- ② wms_item_media  商品素材表。只存相对路径，不存二进制。
--    素材库根目录在项目外（~/Pictures/wms-ec-media/），rel_path 的取值形如
--      items/{itemId}-{商品名}/main/01.jpg
--    这个相对路径同时就是将来 Cloudflare R2 的 object key，迁移时前面拼 CDN 域名
--    即可，库里的值一个字都不用改。
--
-- ③ ec_product + ec_product_item  电商商品档案与关联表。
--    wms_item 按品牌拆行（SDA薄型气缸 13 个品牌各一行），电商侧应当合并成一个商品，
--    品牌作为销售属性。239 个 wms_item 行归组为 148 个电商商品，
--    经核对无一组跨越多个电商类目。
--    电商字段（长标题、卖点、属性 JSON）单独放这里，不进 wms_item 这张打单热表。
--
-- 幂等：ALTER/CREATE 走 information_schema 判断，INSERT 用 IGNORE + NOT EXISTS 守卫。
-- 回滚：执行同目录 ec_product_media_rollback.sql
-- 备份：backups/LOCAL_ry-vue_pre-schema-fix_20260823_145546.sql
-- 依赖：ec_category_normalize.sql（需要 wms_item_category.ec_category_id）
-- ============================================================


-- ------------------------------------------------------------
-- ① 包装尺寸三列
-- ------------------------------------------------------------
SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_item_sku'
                      AND COLUMN_NAME='pack_length'),
  'SELECT ''pack_length 已存在''',
  'ALTER TABLE `wms_item_sku` ADD COLUMN `pack_length` decimal(10,1) NULL DEFAULT NULL COMMENT ''包装长(cm)'' AFTER `height`');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_item_sku'
                      AND COLUMN_NAME='pack_width'),
  'SELECT ''pack_width 已存在''',
  'ALTER TABLE `wms_item_sku` ADD COLUMN `pack_width` decimal(10,1) NULL DEFAULT NULL COMMENT ''包装宽(cm)'' AFTER `pack_length`');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_item_sku'
                      AND COLUMN_NAME='pack_height'),
  'SELECT ''pack_height 已存在''',
  'ALTER TABLE `wms_item_sku` ADD COLUMN `pack_height` decimal(10,1) NULL DEFAULT NULL COMMENT ''包装高(cm)'' AFTER `pack_width`');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;


-- ------------------------------------------------------------
-- ② 商品素材表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wms_item_media` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `item_id`     bigint       NOT NULL COMMENT '商品ID，关联 wms_item.id',
  `sku_id`      bigint       NULL     COMMENT 'SKU ID，规格图才填',
  `media_type`  varchar(16)  NOT NULL COMMENT 'main主图 / detail详情图 / sku规格图 / video视频',
  `rel_path`    varchar(500) NOT NULL COMMENT '素材库相对路径，同时是未来 R2 的 object key',
  `sort_no`     int          NULL DEFAULT 0 COMMENT '同类型内排序，main 的 0 号为白底首图',
  `remark`      varchar(255) NULL,
  `create_by`   varchar(64)  NULL,
  `create_time` datetime(3)  NULL,
  `update_by`   varchar(64)  NULL,
  `update_time` datetime(3)  NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rel_path` (`rel_path`),
  KEY `idx_item_id` (`item_id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_type` (`media_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品素材（只存路径）';


-- ------------------------------------------------------------
-- ③ 电商商品档案
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ec_product` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `ec_name`         varchar(60)  NOT NULL COMMENT '归组名（取自 wms_item.item_name）',
  `ec_category_id`  bigint       NULL     COMMENT '电商类目，关联 ec_category.id',
  `ec_title`        varchar(120) NULL     COMMENT '电商长标题，30-60 字',
  `selling_points`  varchar(500) NULL     COMMENT '卖点，换行分隔',
  `attrs`           json         NULL     COMMENT '结构化属性：材质/接口螺纹/工作压力/温度范围等',
  `status`          varchar(20)  NOT NULL DEFAULT '待整理' COMMENT '待整理/待拍图/可上架/已上架',
  `remark`          varchar(255) NULL,
  `create_by`       varchar(64)  NULL,
  `create_time`     datetime(3)  NULL,
  `update_by`       varchar(64)  NULL,
  `update_time`     datetime(3)  NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ec_name` (`ec_name`),
  KEY `idx_ec_category` (`ec_category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='电商商品档案';

CREATE TABLE IF NOT EXISTS `ec_product_item` (
  `id`            bigint      NOT NULL AUTO_INCREMENT,
  `ec_product_id` bigint      NOT NULL COMMENT '关联 ec_product.id',
  `item_id`       bigint      NOT NULL COMMENT '关联 wms_item.id',
  `create_by`     varchar(64) NULL,
  `create_time`   datetime(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item` (`item_id`),
  KEY `idx_ec_product` (`ec_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='电商商品-仓库商品关联（同名不同品牌归为一个电商商品）';


-- ------------------------------------------------------------
-- ④ 从现有数据初始化 ec_product / ec_product_item
--    按 item_name 归组；ec_category_id 取该组商品所属的电商类目
-- ------------------------------------------------------------
INSERT IGNORE INTO `ec_product` (ec_name, ec_category_id, status, create_by, create_time)
SELECT i.item_name, MIN(c.ec_category_id), '待整理', 'system', NOW(3)
  FROM wms_item i
  JOIN wms_item_category c ON c.id = i.item_category
 GROUP BY i.item_name;

INSERT IGNORE INTO `ec_product_item` (ec_product_id, item_id, create_by, create_time)
SELECT p.id, i.id, 'system', NOW(3)
  FROM wms_item i
  JOIN ec_product p ON p.ec_name = i.item_name;


-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
SELECT '包装尺寸列' AS check_name, COLUMN_NAME, COLUMN_TYPE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_item_sku'
   AND COLUMN_NAME IN ('pack_length','pack_width','pack_height');

SELECT '电商商品数（应 148）' AS check_name, COUNT(*) AS n FROM ec_product;
SELECT '关联行数（应 239）'   AS check_name, COUNT(*) AS n FROM ec_product_item;

SELECT '未归组的商品（应为空）' AS check_name, i.id, i.item_name
  FROM wms_item i
  LEFT JOIN ec_product_item pi ON pi.item_id = i.id
 WHERE pi.id IS NULL;

SELECT '缺电商类目的电商商品（应为空）' AS check_name, id, ec_name
  FROM ec_product WHERE ec_category_id IS NULL;
