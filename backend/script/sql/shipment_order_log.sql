-- ----------------------------
-- 出库单变更历史
--
-- 纯记录单是「事后备查」用的，改动比正常出库单频繁得多（当时价格记错了、
-- 客户后来又加了一项）。单据表上只有 update_by / update_time，只能看到
-- 「最后一次是谁改的」，看不到改了什么、之前是多少——而这恰恰是回头查价格时
-- 最想知道的。为此单独留一张流水表，每次新建/修改/作废各写一条。
--
-- summary 存人类可读的变更摘要（「销售价 2230.00 → 2500.00」这种），
-- 直接给人看，不做结构化解析，所以不用 JSON。
--
-- 表对所有出库单都写，不只纯记录单：正常出库单出库后不能再改，
-- 它的历史通常就是建单 + 出库两条，成本极低。
--
-- 脚本幂等，可重复执行。
-- ----------------------------

CREATE TABLE IF NOT EXISTS `wms_shipment_order_log` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `order_id` bigint(20) NOT NULL COMMENT '出库单id',
  `order_no` varchar(22) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '出库单号，单据删掉后仍能看出是哪张',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '动作：CREATE 新建 / UPDATE 修改 / SHIPMENT 出库 / VOID 作废',
  `summary` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '变更摘要，人类可读',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人',
  `create_time` datetime(3) NULL DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id` (`order_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '出库单变更历史' ROW_FORMAT = Dynamic;

-- 已有单据补一条建单记录，否则历史面板对老单子是空的，看着像丢了数据。
-- 用单据自己的 create_by / create_time，不假装是现在补的。
INSERT INTO `wms_shipment_order_log` (`id`, `order_id`, `order_no`, `action`, `summary`, `create_by`, `create_time`)
SELECT o.`id`, o.`id`, o.`order_no`, 'CREATE', '建单（本条为历史数据补录，早于变更历史功能上线）',
       o.`create_by`, o.`create_time`
  FROM `wms_shipment_order` o
 WHERE NOT EXISTS (SELECT 1 FROM `wms_shipment_order_log` l WHERE l.`order_id` = o.`id`);
