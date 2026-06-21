-- ----------------------------
-- 货架位置可视化：给 wms_location 增加「所属仓库」字段
-- 货架坐标直接用位置编码表示，格式 楼层-排列-格，例如 2-B2-3 = 2楼/B排/第2列/第3格
-- 不同仓库的货架通过 warehouse_id 区分。执行一次即可。
-- ----------------------------

ALTER TABLE `wms_location`
  ADD COLUMN `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '所属仓库' AFTER `location_code`;

-- 货架3D布局（每个仓库一份 JSON，记录各排货架在平面上的位置）
ALTER TABLE `wms_warehouse`
  ADD COLUMN `shelf_layout` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '货架3D布局(JSON)' AFTER `remark`;
