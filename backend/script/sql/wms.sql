-- SAFE GUARD
-- 此文件不再用于初始化数据库，防止误执行导致整库数据被 DROP。
-- 完整初始化脚本已改名为：wms.full-init.DANGEROUS.sql
-- 日常升级只允许执行 README.md 中列出的增量迁移脚本。

SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT = '禁止执行 wms.sql：这是受保护入口。请执行明确命名的增量迁移脚本。';
