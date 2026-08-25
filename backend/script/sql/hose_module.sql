-- ============================================================
-- 压油管模块：胶管 / 接头 / 扣压外套 / 扣压参数
--
-- 为什么不复用 wms_item + wms_inventory：
--   ① 胶管必须按「段」存。1602 合计 32 米，实际是 10+10+4+8 四段，接一张 12 米的
--      单子一段都不够。wms_inventory 的「一 SKU 一数量」模型对胶管直接不成立，
--      查询条件是 length_m >= 需求，不是 SUM(quantity) >= 需求。
--      所以单开 wms_hose_piece，一段一行。
--   ② 接头的编码维度（螺纹 × A/C/D型 × 直/弯 × 芯/面）跟全库的
--      item/sku 两层结构对不上，硬塞进去会把 240 个 SKU 摊到 wms_item 里，
--      污染打单热表。
--   接头/外套是可数件，逻辑上可以走 wms_inventory，但为了和胶管在同一个页面里
--   一次查完，这里一并单独建表；将来要并回主库存体系时按 sku 编码对齐即可。
--
-- 现场编号规则（用户实际在用，不要改成 DN）：
--   胶管 4 位码 = 前两位内径mm + 后两位层数，例 1302 = 内径13(1/2" 四分) 二层钢丝。
--   接头 = 螺纹 + A/C/D型 + [弯] + 芯/面，例 M22x1.5-A-芯、G3/8-C-弯-面。
--   A/C/D 是接头内部密封座形状（现场码），不是弯头角度。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；种子数据在 hose_module_seed.sql，用 INSERT IGNORE。
-- 回滚：执行同目录 hose_module_rollback.sql
-- 依赖：wms_location（库位）、wms_warehouse
-- ============================================================

SET NAMES utf8mb4;


-- ------------------------------------------------------------
-- ① 胶管规格
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wms_hose_spec` (
  `id`                bigint       NOT NULL AUTO_INCREMENT,
  `hose_code`         varchar(8)   NOT NULL COMMENT '现场4位码：内径mm(2位)+层数(2位)，如 1302',
  `bore_code`         varchar(4)   NOT NULL COMMENT '内径码 06/08/10/13/16/19/22/25/32/38/51',
  `layer_code`        varchar(4)   NOT NULL COMMENT '层数码 01/02/03/04/06/00',
  `inch`              varchar(10)  NULL COMMENT '英寸，如 1/2"',
  `nickname`          varchar(16)  NULL COMMENT '俗称，如 四分。客户电话里说的是这个',
  `layer_name`        varchar(32)  NULL COMMENT '层数全称，如 二层钢丝编织',
  `id_mm`             decimal(6,2) NULL COMMENT '内径mm',
  `od_mm`             decimal(6,2) NULL COMMENT '外径mm，欧标参考值，22通径无标准留空',
  `work_pressure_mpa` decimal(6,2) NULL COMMENT '工作压力MPa，欧标参考值',
  `bend_radius_mm`    int          NULL COMMENT '最小弯曲半径mm',
  `cost_price`        decimal(10,2) NULL COMMENT '成本价 元/米',
  `price_source`      varchar(8)   NULL COMMENT '实价 / 推算。推算值误差按 ±15% 看',
  `std_ref`           varchar(64)  NULL COMMENT '对应标准',
  `remark`            varchar(255) NULL,
  `create_by`   varchar(64) NULL,
  `create_time` datetime(3) NULL,
  `update_by`   varchar(64) NULL,
  `update_time` datetime(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hose_code` (`hose_code`),
  KEY `idx_bore_layer` (`bore_code`, `layer_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='胶管规格';


-- ------------------------------------------------------------
-- ② 胶管分段库存 —— 一段一行，这是本模块的核心
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wms_hose_piece` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `hose_code`   varchar(8)    NOT NULL COMMENT '关联 wms_hose_spec.hose_code',
  `location_id` bigint        NULL COMMENT '库位，关联 wms_location.id',
  `length_m`    decimal(8,2)  NOT NULL COMMENT '这一段的长度（米）',
  `status`      varchar(8)    NOT NULL DEFAULT '在库' COMMENT '在库 / 已用完',
  `remark`      varchar(255)  NULL,
  `create_by`   varchar(64) NULL,
  `create_time` datetime(3) NULL,
  `update_by`   varchar(64) NULL,
  `update_time` datetime(3) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_hose_code` (`hose_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='胶管分段库存（余料不能合并使用，必须按段记）';


-- ------------------------------------------------------------
-- ③ 胶管接头
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wms_hose_fitting` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `fitting_sku`    varchar(40)  NOT NULL COMMENT '编码：螺纹-型-[弯]-芯/面，如 M22x1.5-A-芯',
  `field_name`     varchar(40)  NOT NULL COMMENT '现场叫法，跟手写标签一字不差，如 22×1.5 A型芯',
  `thread_system`  varchar(8)   NOT NULL COMMENT '公制 / 英制 / 美制',
  `thread_spec`    varchar(24)  NOT NULL COMMENT '标准螺纹规格，如 M22×1.5 / G3/8 / 9/16-18 UNF',
  `seat_type`      varchar(2)   NOT NULL COMMENT '型 A/C/D：接头内部密封座形状，不是角度',
  `seal_std`       varchar(48)  NULL COMMENT '密封形式标准描述，如 60°内锥（英制锥密封）',
  `std_code`       varchar(64)  NULL COMMENT '对应标准/代号，如 DIN 2353 24° / DKOL、DKOS',
  `gender`         varchar(2)   NOT NULL COMMENT '芯=公头(外螺纹) / 面=母头(内螺纹)',
  `angle`          varchar(4)   NOT NULL DEFAULT '直' COMMENT '直 / 弯',
  `bore_hint`      varchar(64)  NULL COMMENT '可配管通径（参考）。公制螺纹有轻/重系列两解，只做提示不做筛选',
  `seen_on_sheet`  tinyint      NOT NULL DEFAULT 0 COMMENT '1=2026-08-23 手写盘点纸上出现过',
  `qty`            int          NULL COMMENT '库存个数。NULL=还没盘，0=确认没有，两者含义不同',
  `location_id`    bigint       NULL COMMENT '库位，关联 wms_location.id',
  `cost_price`     decimal(10,2) NULL COMMENT '成本价 元/个',
  `brand`          varchar(32)  NULL,
  `vendor_code`    varchar(32)  NULL COMMENT '厂家代号（20111 这类五位码），各厂不一致，按实物填',
  `remark`         varchar(255) NULL,
  `create_by`   varchar(64) NULL,
  `create_time` datetime(3) NULL,
  `update_by`   varchar(64) NULL,
  `update_time` datetime(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fitting_sku` (`fitting_sku`),
  KEY `idx_thread` (`thread_system`, `thread_spec`),
  KEY `idx_seat` (`seat_type`, `gender`, `angle`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='胶管接头';


-- ------------------------------------------------------------
-- ④ 扣压外套（皮子）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wms_hose_ferrule` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `ferrule_sku`  varchar(40)  NOT NULL COMMENT '编码 F12-13-非剥皮',
  `ferrule_name` varchar(64)  NOT NULL,
  `layer_scope`  varchar(16)  NOT NULL COMMENT '适用层数：1层/2层、3层、4层、6层。三层管外径不同，外套不通用',
  `bore_code`    varchar(4)   NOT NULL,
  `skin_type`    varchar(8)   NOT NULL DEFAULT '不分' COMMENT '非剥皮 / 剥皮 / 不分',
  `qty`          int          NULL COMMENT 'NULL=还没盘',
  `location_id`  bigint       NULL,
  `cost_price`   decimal(10,2) NULL COMMENT '元/个',
  `remark`       varchar(255) NULL,
  `create_by`   varchar(64) NULL,
  `create_time` datetime(3) NULL,
  `update_by`   varchar(64) NULL,
  `update_time` datetime(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ferrule_sku` (`ferrule_sku`),
  KEY `idx_layer_bore` (`layer_scope`, `bore_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='扣压外套';


-- ------------------------------------------------------------
-- ⑤ 扣压参数 —— 教程里「压」这一步要看的数
--    这些值是机器和厂牌相关的，只能现场实测，先建空行占位。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wms_hose_crimp` (
  `id`                bigint       NOT NULL AUTO_INCREMENT,
  `layer_scope`       varchar(16)  NOT NULL,
  `bore_code`         varchar(4)   NOT NULL,
  `crimp_diameter_mm` decimal(6,2) NULL COMMENT '扣压直径mm，实测',
  `die_no`            varchar(24)  NULL COMMENT '模具号',
  `strip_length_mm`   decimal(6,1) NULL COMMENT '剥胶长度mm',
  `insert_depth_mm`   decimal(6,1) NULL COMMENT '接头插入深度mm',
  `press_gear`        varchar(24)  NULL COMMENT '压机档位',
  `shop_can_crimp`    tinyint      NOT NULL DEFAULT 1 COMMENT '1=店里压机压得了；0=超出能力，要去仓库压',
  `remark`            varchar(255) NULL,
  `create_by`   varchar(64) NULL,
  `create_time` datetime(3) NULL,
  `update_by`   varchar(64) NULL,
  `update_time` datetime(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_layer_bore` (`layer_scope`, `bore_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='扣压参数（现场实测后填）';


SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'wms_hose%'
 ORDER BY TABLE_NAME;
