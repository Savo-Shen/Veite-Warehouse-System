-- ----------------------------
-- AI 助手会话历史（按用户隔离）
-- 会话 wms_ai_conversation + 消息 wms_ai_message。执行一次即可。
-- 无需菜单：AI 助手页是登录后通用入口，每个用户只看自己的会话。
-- ----------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for wms_ai_conversation AI 会话
-- ----------------------------
DROP TABLE IF EXISTS `wms_ai_conversation`;
CREATE TABLE `wms_ai_conversation`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会话标题（取首条消息）',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(3) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_update`(`user_id`, `update_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wms_ai_message AI 消息
-- ----------------------------
DROP TABLE IF EXISTS `wms_ai_message`;
CREATE TABLE `wms_ai_message`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `conversation_id` bigint(20) NOT NULL COMMENT '所属会话ID',
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色：user / assistant',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '消息内容',
  `tool_trace` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '工具调用轨迹(JSON)',
  `draft` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '建单草稿(JSON)',
  `elapsed_ms` bigint(20) NULL DEFAULT NULL COMMENT '本次回复耗时(毫秒)',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(3) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation`(`conversation_id`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 消息表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
