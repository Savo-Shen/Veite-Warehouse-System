-- ----------------------------
-- 图片/文件存数据库（免 MinIO / 云 OSS）
-- 1. 新建 sys_oss_blob 表，保存文件二进制内容
-- 2. 新增参数 sys.oss.storageMode：db=存数据库（默认），oss=走原 S3/OSS 配置
-- 说明：开启 db 模式后，上传的文件不再推送到对象存储，而是写入本表；
--       sys_oss.url 记为 /system/oss/blob/{ossId}，由后端公开端点回读，
--       该端点已在 application.yml 的 security.excludes 中放行匿名访问。
-- 脚本幂等，可重复执行。
-- ----------------------------

CREATE TABLE IF NOT EXISTS `sys_oss_blob` (
  `oss_id` bigint(20) NOT NULL COMMENT '对象存储主键（与 sys_oss.oss_id 一致）',
  `content_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MIME 类型',
  `data` longblob NOT NULL COMMENT '文件二进制内容',
  PRIMARY KEY (`oss_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'OSS 文件二进制内容（数据库存储模式）' ROW_FORMAT = Dynamic;

INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT 1940000000000001003, '文件存储方式', 'sys.oss.storageMode', 'db', 'Y', 'admin', NOW(),
       'db=文件存数据库（sys_oss_blob，无需 MinIO/云 OSS）；oss=使用对象存储配置'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'sys.oss.storageMode');
