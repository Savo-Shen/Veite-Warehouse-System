-- 给本次补过 SKU、但 unit 为空的两条既有商品补上单位「个」
-- 只按显式 ID 更新这两条，且只在 unit 为空时才动（幂等）。
-- ⚠️ 生产库里另有 59 条商品 unit 也是空的（PE管/PU管/液压油泵/冷却器 等），
--    它们的单位不是「个」，本脚本刻意不碰。
START TRANSACTION;
UPDATE wms_item SET unit='个', update_by='savo_shen', update_time=NOW(3)
WHERE id IN (1960504147652435969, 1960505692792086529)
  AND (unit IS NULL OR unit='');
COMMIT;

SELECT i.id, c.category_name, i.item_name, CONCAT('unit=[', IFNULL(i.unit,'NULL'), ']') AS 单位
FROM wms_item i LEFT JOIN wms_item_category c ON c.id=i.item_category
WHERE i.id IN (1960504147652435969, 1960505692792086529);
