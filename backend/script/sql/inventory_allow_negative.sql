-- ----------------------------
-- 允许出库扣成负库存
-- 场景：货实际发出去了，但这个规格还没盘过库，系统里没有库存记录。
--       允许先扣成负数留痕，事后盘点补正，而不是把出库单卡死。
-- 开关在「基础资料 → 环境配置 → 库存」页面，改完立即生效（sys_config 有缓存，
-- 通过页面保存会自动刷新；直接改库需要重启后端或清 Redis 的 sys_config 缓存）。
-- 脚本幂等，可重复执行。
-- ----------------------------

INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT 1940000000000001003, '库存-允许负库存出库', 'wms.inventory.allowNegative', 'true', 'Y', 'admin', NOW(),
       '为 true 时，出库遇到库存不足可由操作员二次确认后扣成负库存（用于未盘库商品先出后盘）；为 false 时沿用硬拦截。移库不受此开关影响，始终不允许负库存。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'wms.inventory.allowNegative');
