-- 回滚 hose_module.sql / hose_module_seed.sql —— 整个压油管模块的表全删。
-- 表是新建的，删掉不影响任何既有业务；但接头库存/成本价是人工盘出来的，
-- 删之前先备份：
--   mysqldump ry-vue wms_hose_spec wms_hose_piece wms_hose_fitting wms_hose_ferrule wms_hose_crimp > hose_backup.sql
SET NAMES utf8mb4;
DROP TABLE IF EXISTS `wms_hose_crimp`;
DROP TABLE IF EXISTS `wms_hose_ferrule`;
DROP TABLE IF EXISTS `wms_hose_fitting`;
DROP TABLE IF EXISTS `wms_hose_piece`;
DROP TABLE IF EXISTS `wms_hose_spec`;
SELECT '压油管模块表已删除' AS result;
