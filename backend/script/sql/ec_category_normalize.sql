-- ============================================================
-- 商品分类规范化 + 电商类目映射
--
-- A 部分：修正 wms_item_category 树上的分类错误（不动 wms_item 任何一行）
--   A1  轴向柱塞泵 原为一级类目，与液压油泵平级 —— 柱塞泵本身就是液压泵的一种，移到其下
--   A2  一级类目「电磁阀」下挂着手动阀/脚踏阀/溢流阀/液控单向阀，这些都不是电磁阀，
--       类目名过窄导致装错东西 —— 改名为「阀类」，子节点全部不动
--   A3  5 个类目名带「（亚德客型）」兼容性描述 —— 与 2026-08-14 的「商品名去品牌」
--       迁移自相矛盾，去掉；「亚德客型」应作为商品属性/标题关键词
--       注：「SU系列标准气缸（内藏式）」的括号是规格说明，按既有约定保留
--
-- B 部分：新增电商类目映射（纯新增，不改动既有结构）
--   B1  建 ec_category 表，20 个电商叶子类目，各平台类目 ID 留空待填
--   B2  wms_item_category 加 ec_category_id 列，把 96 个仓库类目映射到 20 个电商叶子
--
-- 设计前提：仓库类目最深 4 层、叶子是型号系列（拣货按型号翻）；电商类目最深 3 层、
--   叶子到「气动接头」就停，型号是 SPU 和标题关键词。两者形状不同，不合并成一棵树。
--
-- 幂等：所有 UPDATE 带现值守卫，ALTER/CREATE 走 information_schema 判断，重复执行是空操作
-- 回滚：执行同目录 ec_category_normalize_rollback.sql
-- 备份：backups/LOCAL_ry-vue_pre-ec-category_20260823_144646.sql（全库）
--       backups/LOCAL_category_only_pre-ec_20260823_144646.sql（wms_item_category + wms_item）
-- ============================================================


-- ------------------------------------------------------------
-- A1  轴向柱塞泵 → 液压油泵 下
-- ------------------------------------------------------------
UPDATE wms_item_category
   SET parent_id = 2082332979916455938
 WHERE id = 2082332915953319938
   AND category_name = '轴向柱塞泵'
   AND (parent_id = 0 OR parent_id IS NULL);


-- ------------------------------------------------------------
-- A2  一级类目 电磁阀 → 阀类
-- ------------------------------------------------------------
UPDATE wms_item_category
   SET category_name = '阀类'
 WHERE id = 1950101112237367298
   AND category_name = '电磁阀';


-- ------------------------------------------------------------
-- A3  去掉类目名里的「（亚德客型）」
-- ------------------------------------------------------------
UPDATE wms_item_category SET category_name = 'SC系列标准气缸'
 WHERE id = 1945740847748255746 AND category_name = 'SC系列（亚德客型）';

UPDATE wms_item_category SET category_name = 'SDA薄型气缸'
 WHERE id = 1947134362210308098 AND category_name = 'SDA薄型气缸（亚德客型）';

UPDATE wms_item_category SET category_name = 'MAL铝合金迷你气缸'
 WHERE id = 1947192395711942658 AND category_name = 'MAL铝合金迷你气缸（亚德客型）';

UPDATE wms_item_category SET category_name = 'MA系列不锈钢迷你气缸'
 WHERE id = 1950745480891736066 AND category_name = 'MA系列不锈钢迷你气缸（亚德客型）';

UPDATE wms_item_category SET category_name = 'TN双杆气缸'
 WHERE id = 1951559179839741953 AND category_name = 'TN双杆气缸（亚德客型）';


-- ------------------------------------------------------------
-- B1  电商类目表
--     platform id 三列留空，需到各平台商家后台搜到叶子类目后回填：
--       拼多多  商家后台 → 商品管理 → 发布商品 → 类目搜索
--       淘宝    卖家中心 → 发布宝贝 → 类目搜索
--       抖音    商家后台 → 商品 → 发布商品
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ec_category` (
  `id`           bigint       NOT NULL COMMENT '电商类目ID',
  `ec_code`      varchar(32)  NOT NULL COMMENT '内部编码',
  `ec_l1`        varchar(30)  NOT NULL COMMENT '电商一级类目',
  `ec_l2`        varchar(30)  NOT NULL COMMENT '电商二级类目（叶子）',
  `pdd_cat_id`   varchar(64)  NULL COMMENT '拼多多叶子类目ID，待填',
  `pdd_cat_path` varchar(255) NULL COMMENT '拼多多类目全路径，待填',
  `tb_cat_id`    varchar(64)  NULL COMMENT '淘宝叶子类目ID，待填',
  `tb_cat_path`  varchar(255) NULL COMMENT '淘宝类目全路径，待填',
  `dy_cat_id`    varchar(64)  NULL COMMENT '抖音叶子类目ID，待填',
  `dy_cat_path`  varchar(255) NULL COMMENT '抖音类目全路径，待填',
  `order_num`    int          NULL DEFAULT 0 COMMENT '显示顺序',
  `remark`       varchar(255) NULL COMMENT '备注',
  `create_by`    varchar(64)  NULL,
  `create_time`  datetime(3)  NULL,
  `update_by`    varchar(64)  NULL,
  `update_time`  datetime(3)  NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ec_code` (`ec_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='电商类目（多平台映射）';

INSERT IGNORE INTO `ec_category`
  (id, ec_code, ec_l1, ec_l2, order_num, create_by, create_time) VALUES
  ( 1,'PNEU_CYL',        '气动元件','气缸',        10,'system',NOW(3)),
  ( 2,'PNEU_CYL_PART',   '气动元件','气缸配件',    20,'system',NOW(3)),
  ( 3,'PNEU_SOL_VALVE',  '气动元件','气动电磁阀',  30,'system',NOW(3)),
  ( 4,'PNEU_CTRL_VALVE', '气动元件','气动控制阀',  40,'system',NOW(3)),
  ( 5,'PNEU_FITTING',    '气动元件','气动接头',    50,'system',NOW(3)),
  ( 6,'PNEU_TUBE',       '气动元件','气管',        60,'system',NOW(3)),
  ( 7,'PNEU_FRL',        '气动元件','气源处理器',  70,'system',NOW(3)),
  ( 8,'PNEU_TOOL',       '气动元件','气动工具',    80,'system',NOW(3)),
  ( 9,'HYD_CYL',         '液压元件','液压油缸',   110,'system',NOW(3)),
  (10,'HYD_VALVE',       '液压元件','液压阀',     120,'system',NOW(3)),
  (11,'HYD_PUMP',        '液压元件','液压泵',     130,'system',NOW(3)),
  (12,'HYD_FITTING',     '液压元件','液压接头',   140,'system',NOW(3)),
  (13,'HYD_GAUGE',       '液压元件','压力表',     150,'system',NOW(3)),
  (14,'HYD_PART',        '液压元件','液压配件',   160,'system',NOW(3)),
  (15,'FLU_WATER_VALVE', '流体管路','电磁水阀',   210,'system',NOW(3)),
  (16,'FLU_BALL_VALVE',  '流体管路','球阀',       220,'system',NOW(3)),
  (17,'FLU_PIPE_FITTING','流体管路','管件接头',   230,'system',NOW(3)),
  (18,'FLU_CLAMP',       '流体管路','喉箍',       240,'system',NOW(3)),
  (19,'FLU_COOLANT',     '流体管路','冷却管',     250,'system',NOW(3)),
  (20,'FLU_GREASE_NIP',  '流体管路','黄油嘴',     260,'system',NOW(3));


-- ------------------------------------------------------------
-- B2  wms_item_category 加 ec_category_id 列
-- ------------------------------------------------------------
SET @add_ec_col = IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME   = 'wms_item_category'
            AND COLUMN_NAME  = 'ec_category_id'),
  'SELECT ''wms_item_category.ec_category_id 已存在''',
  'ALTER TABLE `wms_item_category` ADD COLUMN `ec_category_id` bigint NULL DEFAULT NULL COMMENT ''电商类目ID，关联 ec_category.id'' AFTER `status`'
);
PREPARE s FROM @add_ec_col; EXECUTE s; DEALLOCATE PREPARE s;

SET @add_ec_idx = IF(
  EXISTS(SELECT 1 FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME   = 'wms_item_category'
            AND INDEX_NAME   = 'idx_ec_category_id'),
  'SELECT ''idx_ec_category_id 已存在''',
  'CREATE INDEX `idx_ec_category_id` ON `wms_item_category` (`ec_category_id`)'
);
PREPARE s FROM @add_ec_idx; EXECUTE s; DEALLOCATE PREPARE s;


-- ------------------------------------------------------------
-- B3  映射：先算出每个类目节点的根 ID 与「是否在气动接头子树下」
--     全部按 ID 判断，不依赖类目名，A 部分的改名不影响这里
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_cat_root;
CREATE TEMPORARY TABLE tmp_cat_root (
  id           bigint PRIMARY KEY,
  root_id      bigint,
  lvl          int,
  in_pneu_fit  tinyint
);

INSERT INTO tmp_cat_root (id, root_id, lvl, in_pneu_fit)
WITH RECURSIVE t AS (
  SELECT id, id AS root_id, 1 AS lvl,
         CASE WHEN id = 1946489713837772801 THEN 1 ELSE 0 END AS in_pneu_fit
    FROM wms_item_category
   WHERE parent_id = 0 OR parent_id IS NULL
  UNION ALL
  SELECT c.id, t.root_id, t.lvl + 1,
         CASE WHEN c.id = 1946489713837772801 OR t.in_pneu_fit = 1 THEN 1 ELSE 0 END
    FROM wms_item_category c
    JOIN t ON c.parent_id = t.id
)
SELECT id, root_id, lvl, in_pneu_fit FROM t;

UPDATE wms_item_category c
  JOIN tmp_cat_root r ON r.id = c.id
   SET c.ec_category_id = CASE

     -- 气缸支线
     WHEN c.id      = 1945721463986229250 THEN 2   -- 气缸 > 配件
     WHEN r.root_id = 1945721372810448898 THEN 1   -- 气缸

     -- 气管 / 气源处理 / 吹尘枪
     WHEN r.root_id = 1945406757392125954 THEN 6   -- 气管
     WHEN r.root_id = 1957000462511161345 THEN 7   -- 气源处理类
     WHEN r.root_id = 1945738146599702529 THEN 8   -- 吹尘枪

     -- 阀类（原一级类目「电磁阀」）按子节点拆到三个电商类目
     WHEN c.id IN (1957281684021886978,             -- 气动电磁阀
                   2082349444849745921,             -- 电磁换向阀
                   1976863395529367554) THEN 3      -- 电磁阀配件
     WHEN c.id IN (1950101225299025921,             -- 手动阀
                   1950101259419688962,             -- 脚踏阀
                   2082348810058612738) THEN 4      -- 手动换向阀
     WHEN c.id IN (1957323749183242242,             -- 液压电磁阀
                   1957407607064936450,             -- 液压手动阀
                   2082347652363599874,             -- 溢流阀
                   2082348255093473281,             -- 液控单向阀
                   2082349233964335105) THEN 10     -- 电磁调速阀
     WHEN c.id IN (1950101174749274114,             -- 电磁水阀
                   1961769056910520321) THEN 15     -- 制冷电磁阀
     WHEN r.root_id = 1950101112237367298 THEN 3    -- 阀类根节点兜底

     -- 液压支线
     WHEN r.root_id = 1950554864501137410 THEN 9    -- 油缸
     WHEN r.root_id = 2082332979916455938 THEN 11   -- 液压油泵（含 A1 移入的轴向柱塞泵）
     WHEN r.root_id = 1945335704057815041 THEN 12   -- 液压接头
     WHEN r.root_id = 1960505384837898241 THEN 13   -- 油压表
     WHEN r.root_id = 1959536968098373633 THEN 14   -- 过滤网

     -- 流体管路
     WHEN r.root_id = 1959067693386600450 THEN 19   -- 冷却管
     WHEN r.root_id = 1956999725878132738 THEN 20   -- 黄油嘴
     WHEN r.in_pneu_fit = 1               THEN 5    -- 管路连接类 > 气动接头 整个子树
     WHEN c.id      = 1995063596882673665 THEN 16   -- 球阀
     WHEN c.id      = 1977280199850467330 THEN 18   -- 喉箍
     WHEN r.root_id = 1958495846890749954 THEN 17   -- 管路连接类其余（管件接头）

     ELSE c.ec_category_id
   END;

DROP TEMPORARY TABLE IF EXISTS tmp_cat_root;


-- ------------------------------------------------------------
-- 校验：以下两条都必须返回 0 行
-- ------------------------------------------------------------
SELECT '未映射的类目节点（应为空）' AS check_name, id, category_name
  FROM wms_item_category WHERE ec_category_id IS NULL;

SELECT '归属未映射类目的商品（应为空）' AS check_name, i.id, i.item_name
  FROM wms_item i
  JOIN wms_item_category c ON c.id = i.item_category
 WHERE c.ec_category_id IS NULL;
