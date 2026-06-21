-- ----------------------------
-- 商品标签模块（标签 + 商品标签关联 + 菜单权限）
-- 执行一次即可。菜单默认挂在「基础资料」目录下。
-- 超级管理员(admin)自动拥有全部菜单；其它角色需在「角色管理」中授权。
-- ----------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for wms_item_tag 商品标签表
-- ----------------------------
DROP TABLE IF EXISTS `wms_item_tag`;
CREATE TABLE `wms_item_tag`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签名称',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '#409EFF' COMMENT '标签颜色',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(3) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tag_name`(`tag_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wms_item_tag_rel 商品-标签 关联表
-- ----------------------------
DROP TABLE IF EXISTS `wms_item_tag_rel`;
CREATE TABLE `wms_item_tag_rel`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `item_id` bigint(20) NOT NULL COMMENT '商品ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(3) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_item_tag`(`item_id`, `tag_id`) USING BTREE,
  INDEX `idx_item_id`(`item_id`) USING BTREE,
  INDEX `idx_tag_id`(`tag_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 菜单 sys_menu （挂在「基础资料」目录 1808758090157985794 下）
-- ----------------------------
DELETE FROM `sys_menu` WHERE `menu_id` IN (1940000000000000001, 1940000000000000002, 1940000000000000003);

INSERT INTO `sys_menu` VALUES (1940000000000000001, '标签管理', 1808758090157985794, 5, 'itemTag', 'wms/basic/itemTag/index', NULL, 0, 0, 'C', '1', '1', 'wms:itemTag:list', 'documentation', 'admin', '2026-06-20 00:00:00', 'admin', '2026-06-20 00:00:00', '商品标签管理菜单');
INSERT INTO `sys_menu` VALUES (1940000000000000002, '标签查询', 1940000000000000001, 1, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:itemTag:list', '#', 'admin', '2026-06-20 00:00:00', 'admin', '2026-06-20 00:00:00', '');
INSERT INTO `sys_menu` VALUES (1940000000000000003, '标签编辑', 1940000000000000001, 2, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:itemTag:edit', '#', 'admin', '2026-06-20 00:00:00', 'admin', '2026-06-20 00:00:00', '');

SET FOREIGN_KEY_CHECKS = 1;
