-- 待入库拍照手记 → 商品/SKU 主数据落地
-- 来源: output/待入库录入/待入库拍照手记_录入正稿.xlsx（108 行，跳过 6 行，落地 102 行）
-- 本脚本只建 品牌/分类/商品/SKU，不开入库单、不写库存、不记价格。
-- 幂等：每个对象先查后插，重复执行不会重复创建。
-- 生成时间: 2026-08-26

SET NAMES utf8mb4;
SET @OP := 'savo_shen';
SET @TS := NOW(3);
SET autocommit = 0;
START TRANSACTION;

-- ============ 1. 新建品牌 ============
SET @brand_1 := (SELECT id FROM wms_item_brand WHERE brand_name='照庆' LIMIT 1);
INSERT INTO wms_item_brand (brand_name, create_by, create_time, update_by, update_time)
  SELECT '照庆', @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @brand_1 IS NULL;
SET @brand_1 := IFNULL(@brand_1, LAST_INSERT_ID());

-- ============ 2. 新建分类 ============
SET @pcat_1 := (SELECT id FROM wms_item_category WHERE category_name='气缸' LIMIT 1);
SET @cat_1 := (SELECT id FROM wms_item_category WHERE category_name='手指气缸' LIMIT 1);
INSERT INTO wms_item_category (parent_id, category_name, order_num, status, create_by, create_time, update_by, update_time)
  SELECT @pcat_1, '手指气缸', 9, '1', @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @cat_1 IS NULL;
SET @cat_1 := IFNULL(@cat_1, LAST_INSERT_ID());
-- 手指气缸  父级=气缸

SET @pcat_2 := (SELECT id FROM wms_item_category WHERE category_name='液压油泵' LIMIT 1);
SET @cat_2 := (SELECT id FROM wms_item_category WHERE category_name='抽油泵' LIMIT 1);
INSERT INTO wms_item_category (parent_id, category_name, order_num, status, create_by, create_time, update_by, update_time)
  SELECT @pcat_2, '抽油泵', 13, '1', @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @cat_2 IS NULL;
SET @cat_2 := IFNULL(@cat_2, LAST_INSERT_ID());
-- 抽油泵  父级=液压油泵

SET @pcat_3 := (SELECT id FROM wms_item_category WHERE category_name='阀类' LIMIT 1);
SET @cat_3 := (SELECT id FROM wms_item_category WHERE category_name='单向节流阀' LIMIT 1);
INSERT INTO wms_item_category (parent_id, category_name, order_num, status, create_by, create_time, update_by, update_time)
  SELECT @pcat_3, '单向节流阀', 13, '1', @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @cat_3 IS NULL;
SET @cat_3 := IFNULL(@cat_3, LAST_INSERT_ID());
-- 单向节流阀  父级=阀类

-- ============ 3. 新建商品 + 其下 SKU ============
-- ---- 『气源处理类』/『自动排水器』/品牌『(空)』  1 个SKU ----
SET @c1 := (SELECT id FROM wms_item_category WHERE category_name='气源处理类' LIMIT 1);
SET @it1 := (SELECT id FROM wms_item WHERE item_name='自动排水器' AND item_category=@c1 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '自动排水器', @c1, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it1 IS NULL;
SET @it1 := IFNULL(@it1, LAST_INSERT_ID());
SET @s1_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it1 AND sku_name='XFC400' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'XFC400', @it1, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s1_1 IS NULL;  -- #9 IMG_3087 数量43个

-- ---- 『手指气缸』/『MHY2 气动手指』/品牌『(空)』  1 个SKU ----
SET @it2 := (SELECT id FROM wms_item WHERE item_name='MHY2 气动手指' AND item_category=@cat_1 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'MHY2 气动手指', @cat_1, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it2 IS NULL;
SET @it2 := IFNULL(@it2, LAST_INSERT_ID());
SET @s2_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it2 AND sku_name='MHY2-20D' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'MHY2-20D', @it2, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s2_1 IS NULL;  -- #30 IMG_3088 数量1个

-- ---- 『手指气缸』/『HFZ 气动手指』/品牌『(空)』  1 个SKU ----
SET @it3 := (SELECT id FROM wms_item WHERE item_name='HFZ 气动手指' AND item_category=@cat_1 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'HFZ 气动手指', @cat_1, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it3 IS NULL;
SET @it3 := IFNULL(@it3, LAST_INSERT_ID());
SET @s3_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it3 AND sku_name='HFZ-16D' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'HFZ-16D', @it3, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s3_1 IS NULL;  -- #31 IMG_3088 数量1个

-- ---- 『油压表』/『压力表』/品牌『(空)』  1 个SKU ----
SET @c4 := (SELECT id FROM wms_item_category WHERE category_name='油压表' LIMIT 1);
SET @it4 := (SELECT id FROM wms_item WHERE item_name='压力表' AND item_category=@c4 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '压力表', @c4, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it4 IS NULL;
SET @it4 := IFNULL(@it4, LAST_INSERT_ID());
SET @s4_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it4 AND sku_name='2分牙50面板 10kg/1MPa' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '2分牙50面板 10kg/1MPa', @it4, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s4_1 IS NULL;  -- #34 IMG_3088 数量109个

-- ---- 『气动电磁阀』/『DQK 电磁阀』/品牌『(空)』  4 个SKU ----
SET @c5 := (SELECT id FROM wms_item_category WHERE category_name='气动电磁阀' LIMIT 1);
SET @it5 := (SELECT id FROM wms_item WHERE item_name='DQK 电磁阀' AND item_category=@c5 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'DQK 电磁阀', @c5, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it5 IS NULL;
SET @it5 := IFNULL(@it5, LAST_INSERT_ID());
SET @s5_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it5 AND sku_name='DQK1442' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'DQK1442', @it5, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s5_1 IS NULL;  -- #45 IMG_3089 数量1个
SET @s5_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it5 AND sku_name='DQK2422' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'DQK2422', @it5, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s5_2 IS NULL;  -- #46 IMG_3089 数量7个
SET @s5_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it5 AND sku_name='DQK1422 220V' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'DQK1422 220V', @it5, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s5_3 IS NULL;  -- #47 IMG_3089 数量10个
SET @s5_4 := (SELECT id FROM wms_item_sku WHERE item_id=@it5 AND sku_name='DQK1322 24V' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'DQK1322 24V', @it5, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s5_4 IS NULL;  -- #51 IMG_3089 数量1个

-- ---- 『调压过滤器（二联件）』/『三联体』/品牌『JYC』  1 个SKU ----
SET @c6 := (SELECT id FROM wms_item_category WHERE category_name='调压过滤器（二联件）' LIMIT 1);
SET @b6 := (SELECT id FROM wms_item_brand WHERE brand_name='JYC' LIMIT 1);
SET @it6 := (SELECT id FROM wms_item WHERE item_name='三联体' AND item_category=@c6 AND item_brand=@b6 LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '三联体', @c6, '个', @b6, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it6 IS NULL;
SET @it6 := IFNULL(@it6, LAST_INSERT_ID());
SET @s6_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it6 AND sku_name='SFC2000-1/4' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'SFC2000-1/4', @it6, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s6_1 IS NULL;  -- #48 IMG_3089 数量9个

-- ---- 『调压过滤器（二联件）』/『调压过滤器』/品牌『百灵（BLCH）』  2 个SKU ----
SET @c7 := (SELECT id FROM wms_item_category WHERE category_name='调压过滤器（二联件）' LIMIT 1);
SET @b7 := (SELECT id FROM wms_item_brand WHERE brand_name='百灵（BLCH）' LIMIT 1);
SET @it7 := (SELECT id FROM wms_item WHERE item_name='调压过滤器' AND item_category=@c7 AND item_brand=@b7 LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '调压过滤器', @c7, '个', @b7, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it7 IS NULL;
SET @it7 := IFNULL(@it7, LAST_INSERT_ID());
SET @s7_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it7 AND sku_name='2010-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '2010-02', @it7, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s7_1 IS NULL;  -- #49 IMG_3089 数量28个
SET @s7_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it7 AND sku_name='AFR2000-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AFR2000-02', @it7, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s7_2 IS NULL;  -- #50 IMG_3089 数量295个

-- ---- 『抽油泵』/『抽油泵』/品牌『(空)』  1 个SKU ----
SET @it8 := (SELECT id FROM wms_item WHERE item_name='抽油泵' AND item_category=@cat_2 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '抽油泵', @cat_2, '台', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it8 IS NULL;
SET @it8 := IFNULL(@it8, LAST_INSERT_ID());
SET @s8_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it8 AND sku_name='3-PHASE 1/4HP' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '3-PHASE 1/4HP', @it8, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s8_1 IS NULL;  -- #62 IMG_3090 数量1台

-- ---- 『手动阀』/『气动手动阀』/品牌『(空)』  6 个SKU ----
SET @c9 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @it9 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c9 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '气动手动阀', @c9, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it9 IS NULL;
SET @it9 := IFNULL(@it9, LAST_INSERT_ID());
SET @s9_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it9 AND sku_name='S0230605A' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'S0230605A', @it9, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s9_1 IS NULL;  -- #77 IMG_3091 数量19个
SET @s9_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it9 AND sku_name='3R210-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '3R210-08', @it9, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s9_2 IS NULL;  -- #79 IMG_3091 数量34个
SET @s9_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it9 AND sku_name='4R210-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4R210-08', @it9, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s9_3 IS NULL;  -- #80 IMG_3091 数量22个
SET @s9_4 := (SELECT id FROM wms_item_sku WHERE item_id=@it9 AND sku_name='XQ250622' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'XQ250622', @it9, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s9_4 IS NULL;  -- #101 IMG_3092 数量1个
SET @s9_5 := (SELECT id FROM wms_item_sku WHERE item_id=@it9 AND sku_name='XQ230421' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'XQ230421', @it9, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s9_5 IS NULL;  -- #102 IMG_3092 数量35个
SET @s9_6 := (SELECT id FROM wms_item_sku WHERE item_id=@it9 AND sku_name='S3R-M5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'S3R-M5', @it9, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s9_6 IS NULL;  -- #127 IMG_3093 数量15个

-- ---- 『手动阀』/『气动手动阀』/品牌『照庆』  1 个SKU ----
SET @c10 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @it10 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c10 AND item_brand=@brand_1 LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '气动手动阀', @c10, '个', @brand_1, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it10 IS NULL;
SET @it10 := IFNULL(@it10, LAST_INSERT_ID());
SET @s10_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it10 AND sku_name='Q25 RS-L6' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'Q25 RS-L6', @it10, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s10_1 IS NULL;  -- #78 IMG_3091 数量2个

-- ---- 『调压阀』/『QTYH 调压阀』/品牌『(空)』  1 个SKU ----
SET @c11 := (SELECT id FROM wms_item_category WHERE category_name='调压阀' LIMIT 1);
SET @it11 := (SELECT id FROM wms_item WHERE item_name='QTYH 调压阀' AND item_category=@c11 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'QTYH 调压阀', @c11, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it11 IS NULL;
SET @it11 := IFNULL(@it11, LAST_INSERT_ID());
SET @s11_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it11 AND sku_name='QTYH-08 0.5-30MPa' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'QTYH-08 0.5-30MPa', @it11, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s11_1 IS NULL;  -- #81 IMG_3091 数量1个

-- ---- 『气源处理类』/『AF 过滤器』/品牌『(空)』  1 个SKU ----
SET @c12 := (SELECT id FROM wms_item_category WHERE category_name='气源处理类' LIMIT 1);
SET @it12 := (SELECT id FROM wms_item WHERE item_name='AF 过滤器' AND item_category=@c12 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'AF 过滤器', @c12, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it12 IS NULL;
SET @it12 := IFNULL(@it12, LAST_INSERT_ID());
SET @s12_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it12 AND sku_name='AF2000' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AF2000', @it12, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s12_1 IS NULL;  -- #82 IMG_3091 数量30个

-- ---- 『气源处理类』/『AL 油雾器』/品牌『(空)』  1 个SKU ----
SET @c13 := (SELECT id FROM wms_item_category WHERE category_name='气源处理类' LIMIT 1);
SET @it13 := (SELECT id FROM wms_item WHERE item_name='AL 油雾器' AND item_category=@c13 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'AL 油雾器', @c13, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it13 IS NULL;
SET @it13 := IFNULL(@it13, LAST_INSERT_ID());
SET @s13_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it13 AND sku_name='AL2000' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AL2000', @it13, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s13_1 IS NULL;  -- #83 IMG_3091 数量3个

-- ---- 『润滑器』/『斜阀式油杯』/品牌『(空)』  2 个SKU ----
SET @c14 := (SELECT id FROM wms_item_category WHERE category_name='润滑器' LIMIT 1);
SET @it14 := (SELECT id FROM wms_item WHERE item_name='斜阀式油杯' AND item_category=@c14 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '斜阀式油杯', @c14, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it14 IS NULL;
SET @it14 := IFNULL(@it14, LAST_INSERT_ID());
SET @s14_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it14 AND sku_name='B型 50cm² M14X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'B型 50cm² M14X1.5', @it14, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s14_1 IS NULL;  -- #84 IMG_3091 数量2个
SET @s14_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it14 AND sku_name='B型 25cm² M14X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'B型 25cm² M14X1.5', @it14, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s14_2 IS NULL;  -- #85 IMG_3091 数量20个

-- ---- 『配件』/『缓冲器』/品牌『(空)』  1 个SKU ----
SET @c15 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it15 := (SELECT id FROM wms_item WHERE item_name='缓冲器' AND item_category=@c15 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '缓冲器', @c15, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it15 IS NULL;
SET @it15 := IFNULL(@it15, LAST_INSERT_ID());
SET @s15_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it15 AND sku_name='AC3660-2' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AC3660-2', @it15, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s15_1 IS NULL;  -- #86 IMG_3091 数量2个

-- ---- 『调压阀』/『HR 调压阀』/品牌『(空)』  2 个SKU ----
SET @c16 := (SELECT id FROM wms_item_category WHERE category_name='调压阀' LIMIT 1);
SET @it16 := (SELECT id FROM wms_item WHERE item_name='HR 调压阀' AND item_category=@c16 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'HR 调压阀', @c16, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it16 IS NULL;
SET @it16 := IFNULL(@it16, LAST_INSERT_ID());
SET @s16_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it16 AND sku_name='HR-60' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'HR-60', @it16, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s16_1 IS NULL;  -- #87 IMG_3091 数量1个
SET @s16_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it16 AND sku_name='HR-15' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'HR-15', @it16, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s16_2 IS NULL;  -- #88 IMG_3091 数量1个

-- ---- 『气源处理类』/『AC 三联件』/品牌『(空)』  4 个SKU ----
SET @c17 := (SELECT id FROM wms_item_category WHERE category_name='气源处理类' LIMIT 1);
SET @it17 := (SELECT id FROM wms_item WHERE item_name='AC 三联件' AND item_category=@c17 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'AC 三联件', @c17, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it17 IS NULL;
SET @it17 := IFNULL(@it17, LAST_INSERT_ID());
SET @s17_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it17 AND sku_name='AC4225' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AC4225', @it17, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s17_1 IS NULL;  -- #89 IMG_3092 数量5个
SET @s17_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it17 AND sku_name='AC2050-2' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AC2050-2', @it17, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s17_2 IS NULL;  -- #90 IMG_3092 数量3个
SET @s17_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it17 AND sku_name='AC2525-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AC2525-02', @it17, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s17_3 IS NULL;  -- #91 IMG_3092 数量3个
SET @s17_4 := (SELECT id FROM wms_item_sku WHERE item_id=@it17 AND sku_name='AC2540-2' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AC2540-2', @it17, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s17_4 IS NULL;  -- #92 IMG_3092 数量2个

-- ---- 『配件』/『压力开关』/品牌『(空)』  1 个SKU ----
SET @c18 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it18 := (SELECT id FROM wms_item WHERE item_name='压力开关' AND item_category=@c18 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '压力开关', @c18, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it18 IS NULL;
SET @it18 := IFNULL(@it18, LAST_INSERT_ID());
SET @s18_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it18 AND sku_name='110' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '110', @it18, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s18_1 IS NULL;  -- #93 IMG_3092 数量6个

-- ---- 『气动电磁阀』/『4A 气控阀』/品牌『(空)』  7 个SKU ----
SET @c19 := (SELECT id FROM wms_item_category WHERE category_name='气动电磁阀' LIMIT 1);
SET @it19 := (SELECT id FROM wms_item WHERE item_name='4A 气控阀' AND item_category=@c19 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '4A 气控阀', @c19, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it19 IS NULL;
SET @it19 := IFNULL(@it19, LAST_INSERT_ID());
SET @s19_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it19 AND sku_name='4A320-10' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4A320-10', @it19, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s19_1 IS NULL;  -- #95 IMG_3092 数量15个
SET @s19_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it19 AND sku_name='4A230C-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4A230C-08', @it19, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s19_2 IS NULL;  -- #96 IMG_3092 数量45个
SET @s19_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it19 AND sku_name='4A210-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4A210-08', @it19, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s19_3 IS NULL;  -- #97 IMG_3092 数量24个
SET @s19_4 := (SELECT id FROM wms_item_sku WHERE item_id=@it19 AND sku_name='4A110-06' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4A110-06', @it19, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s19_4 IS NULL;  -- #98 IMG_3092 数量45个
SET @s19_5 := (SELECT id FROM wms_item_sku WHERE item_id=@it19 AND sku_name='4A410-15' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4A410-15', @it19, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s19_5 IS NULL;  -- #99 IMG_3092 数量4个
SET @s19_6 := (SELECT id FROM wms_item_sku WHERE item_id=@it19 AND sku_name='4A220-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4A220-08', @it19, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s19_6 IS NULL;  -- #100 IMG_3092 数量12个
SET @s19_7 := (SELECT id FROM wms_item_sku WHERE item_id=@it19 AND sku_name='4A420-15' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4A420-15', @it19, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s19_7 IS NULL;  -- #119 IMG_3093 数量1个

-- ---- 『单向节流阀』/『单向节流阀』/品牌『(空)』  1 个SKU ----
SET @it20 := (SELECT id FROM wms_item WHERE item_name='单向节流阀' AND item_category=@cat_3 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '单向节流阀', @cat_3, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it20 IS NULL;
SET @it20 := IFNULL(@it20, LAST_INSERT_ID());
SET @s20_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it20 AND sku_name='AXC200-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'AXC200-08', @it20, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s20_1 IS NULL;  -- #105 IMG_3092 数量50个

-- ---- 『配件』/『浮动接头』/品牌『(空)』  10 个SKU ----
SET @c21 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it21 := (SELECT id FROM wms_item WHERE item_name='浮动接头' AND item_category=@c21 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '浮动接头', @c21, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it21 IS NULL;
SET @it21 := IFNULL(@it21, LAST_INSERT_ID());
SET @s21_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M14X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M14X1.5', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_1 IS NULL;  -- #106 IMG_3092 数量31个
SET @s21_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M20X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M20X1.5', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_2 IS NULL;  -- #107 IMG_3092 数量20个
SET @s21_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M16X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M16X1.5', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_3 IS NULL;  -- #108 IMG_3092 数量21个
SET @s21_4 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M10X1.25' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M10X1.25', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_4 IS NULL;  -- #109 IMG_3092 数量41个
SET @s21_5 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M8X1.25' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M8X1.25', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_5 IS NULL;  -- #110 IMG_3093 数量28个
SET @s21_6 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M12X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M12X1.5', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_6 IS NULL;  -- #111 IMG_3093 数量76个
SET @s21_7 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M6X1' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M6X1', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_7 IS NULL;  -- #112 IMG_3093 数量40个
SET @s21_8 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M5X0.8' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M5X0.8', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_8 IS NULL;  -- #113 IMG_3093 数量24个
SET @s21_9 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M22X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M22X1.5', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_9 IS NULL;  -- #115 IMG_3093 数量20个
SET @s21_10 := (SELECT id FROM wms_item_sku WHERE item_id=@it21 AND sku_name='M18X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M18X1.5', @it21, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s21_10 IS NULL;  -- #116 IMG_3093 数量20个

-- ---- 『配件』/『JA 浮动接头』/品牌『(空)』  1 个SKU ----
SET @c22 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it22 := (SELECT id FROM wms_item WHERE item_name='JA 浮动接头' AND item_category=@c22 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'JA 浮动接头', @c22, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it22 IS NULL;
SET @it22 := IFNULL(@it22, LAST_INSERT_ID());
SET @s22_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it22 AND sku_name='JA10-4-070' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'JA10-4-070', @it22, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s22_1 IS NULL;  -- #114 IMG_3093 数量21个

-- ---- 『排气阀』/『RE 消声器』/品牌『盛达(SDPC)』  4 个SKU ----
SET @c23 := (SELECT id FROM wms_item_category WHERE category_name='排气阀' LIMIT 1);
SET @b23 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it23 := (SELECT id FROM wms_item WHERE item_name='RE 消声器' AND item_category=@c23 AND item_brand=@b23 LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'RE 消声器', @c23, '个', @b23, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it23 IS NULL;
SET @it23 := IFNULL(@it23, LAST_INSERT_ID());
SET @s23_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it23 AND sku_name='RE-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'RE-02', @it23, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s23_1 IS NULL;  -- #120 IMG_3093 数量39个
SET @s23_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it23 AND sku_name='RE-06' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'RE-06', @it23, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s23_2 IS NULL;  -- #123 IMG_3093 数量27个
SET @s23_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it23 AND sku_name='RE-03' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'RE-03', @it23, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s23_3 IS NULL;  -- #125 IMG_3093 数量27个
SET @s23_4 := (SELECT id FROM wms_item_sku WHERE item_id=@it23 AND sku_name='RE-01' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'RE-01', @it23, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s23_4 IS NULL;  -- #126 IMG_3093 数量105个

-- ---- 『排气阀』/『SE 消声器』/品牌『盛达(SDPC)』  2 个SKU ----
SET @c24 := (SELECT id FROM wms_item_category WHERE category_name='排气阀' LIMIT 1);
SET @b24 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it24 := (SELECT id FROM wms_item WHERE item_name='SE 消声器' AND item_category=@c24 AND item_brand=@b24 LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'SE 消声器', @c24, '个', @b24, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it24 IS NULL;
SET @it24 := IFNULL(@it24, LAST_INSERT_ID());
SET @s24_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it24 AND sku_name='SE-06 2分' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'SE-06 2分', @it24, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s24_1 IS NULL;  -- #121 IMG_3093 数量17个
SET @s24_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it24 AND sku_name='SE-04 4分' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'SE-04 4分', @it24, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s24_2 IS NULL;  -- #122 IMG_3093 数量3个

-- ---- 『排气阀』/『快速排气阀』/品牌『(空)』  4 个SKU ----
SET @c25 := (SELECT id FROM wms_item_category WHERE category_name='排气阀' LIMIT 1);
SET @it25 := (SELECT id FROM wms_item WHERE item_name='快速排气阀' AND item_category=@c25 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '快速排气阀', @c25, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it25 IS NULL;
SET @it25 := IFNULL(@it25, LAST_INSERT_ID());
SET @s25_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it25 AND sku_name='ST-01' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'ST-01', @it25, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s25_1 IS NULL;  -- #128 IMG_3093 数量18个
SET @s25_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it25 AND sku_name='ST-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'ST-08', @it25, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s25_2 IS NULL;  -- #129 IMG_3093 数量2个
SET @s25_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it25 AND sku_name='QE-04' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'QE-04', @it25, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s25_3 IS NULL;  -- #131 IMG_3094 数量63个
SET @s25_4 := (SELECT id FROM wms_item_sku WHERE item_id=@it25 AND sku_name='QE-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'QE-02', @it25, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s25_4 IS NULL;  -- #132 IMG_3094 数量17个

-- ---- 『手动阀』/『气动手动阀』/品牌『亚德客(AirTac)』  1 个SKU ----
SET @c26 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b26 := (SELECT id FROM wms_item_brand WHERE brand_name='亚德客(AirTac)' LIMIT 1);
SET @it26 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c26 AND item_brand=@b26 LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '气动手动阀', @c26, '个', @b26, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it26 IS NULL;
SET @it26 := IFNULL(@it26, LAST_INSERT_ID());
SET @s26_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it26 AND sku_name='S3H-M5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'S3H-M5', @it26, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s26_1 IS NULL;  -- #130 IMG_3093 数量1个

-- ---- 『液压手动阀』/『液压单向阀』/品牌『(空)』  2 个SKU ----
SET @c27 := (SELECT id FROM wms_item_category WHERE category_name='液压手动阀' LIMIT 1);
SET @it27 := (SELECT id FROM wms_item WHERE item_name='液压单向阀' AND item_category=@c27 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '液压单向阀', @c27, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it27 IS NULL;
SET @it27 := IFNULL(@it27, LAST_INSERT_ID());
SET @s27_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it27 AND sku_name='GTC-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'GTC-02', @it27, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s27_1 IS NULL;  -- #133 IMG_3094 数量32个
SET @s27_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it27 AND sku_name='GTC-03' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'GTC-03', @it27, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s27_2 IS NULL;  -- #134 IMG_3094 数量50个

-- ---- 『手动阀』/『HSV 手动阀』/品牌『(空)』  2 个SKU ----
SET @c28 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @it28 := (SELECT id FROM wms_item WHERE item_name='HSV 手动阀' AND item_category=@c28 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'HSV 手动阀', @c28, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it28 IS NULL;
SET @it28 := IFNULL(@it28, LAST_INSERT_ID());
SET @s28_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it28 AND sku_name='HSV-08' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'HSV-08', @it28, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s28_1 IS NULL;  -- #135 IMG_3094 数量1个
SET @s28_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it28 AND sku_name='HSV-10 3分' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'HSV-10 3分', @it28, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s28_2 IS NULL;  -- #136 IMG_3094 数量37个

-- ---- 『配件』/『流量计』/品牌『(空)』  1 个SKU ----
SET @c29 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it29 := (SELECT id FROM wms_item WHERE item_name='流量计' AND item_category=@c29 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '流量计', @c29, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it29 IS NULL;
SET @it29 := IFNULL(@it29, LAST_INSERT_ID());
SET @s29_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it29 AND sku_name='LZT-1005G' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'LZT-1005G', @it29, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s29_1 IS NULL;  -- #137 IMG_3094 数量10个

-- ---- 『宝塔接头』/『宝塔三通』/品牌『(空)』  3 个SKU ----
SET @c30 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it30 := (SELECT id FROM wms_item WHERE item_name='宝塔三通' AND item_category=@c30 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '宝塔三通', @c30, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it30 IS NULL;
SET @it30 := IFNULL(@it30, LAST_INSERT_ID());
SET @s30_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it30 AND sku_name='10' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '10', @it30, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s30_1 IS NULL;  -- #152 IMG_3095 数量298个
SET @s30_2 := (SELECT id FROM wms_item_sku WHERE item_id=@it30 AND sku_name='8' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '8', @it30, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s30_2 IS NULL;  -- #153 IMG_3095 数量611个
SET @s30_3 := (SELECT id FROM wms_item_sku WHERE item_id=@it30 AND sku_name='6' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '6', @it30, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s30_3 IS NULL;  -- #154 IMG_3095 数量89个

-- ---- 『配件』/『铜球』/品牌『(空)』  1 个SKU ----
SET @c31 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it31 := (SELECT id FROM wms_item WHERE item_name='铜球' AND item_category=@c31 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT '铜球', @c31, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it31 IS NULL;
SET @it31 := IFNULL(@it31, LAST_INSERT_ID());
SET @s31_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it31 AND sku_name='8' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '8', @it31, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s31_1 IS NULL;  -- #155 IMG_3096 数量1603个

-- ---- 『气缸』/『CDJ2B 针型气缸』/品牌『(空)』  1 个SKU ----
SET @c32 := (SELECT id FROM wms_item_category WHERE category_name='气缸' LIMIT 1);
SET @it32 := (SELECT id FROM wms_item WHERE item_name='CDJ2B 针型气缸' AND item_category=@c32 AND item_brand IS NULL LIMIT 1);
INSERT INTO wms_item (item_name, item_category, unit, item_brand, create_by, create_time, update_by, update_time)
  SELECT 'CDJ2B 针型气缸', @c32, '个', NULL, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @it32 IS NULL;
SET @it32 := IFNULL(@it32, LAST_INSERT_ID());
SET @s32_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it32 AND sku_name='CDJ2B10-40' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'CDJ2B10-40', @it32, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s32_1 IS NULL;  -- #159 IMG_3096 数量50个

-- ============ 4. 给已有商品补 SKU ============
SET @c33 := (SELECT id FROM wms_item_category WHERE category_name='电磁水阀' LIMIT 1);
SET @b33 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it33 := (SELECT id FROM wms_item WHERE item_name='电磁水阀' AND item_category=@c33 AND item_brand=@b33 LIMIT 1);
SET @s33_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it33 AND sku_name='Q22XD-2L 220V 全铜' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'Q22XD-2L 220V 全铜', @it33, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s33_1 IS NULL;  -- #35 IMG_3088 数量118个

SET @c34 := (SELECT id FROM wms_item_category WHERE category_name='电磁水阀' LIMIT 1);
SET @b34 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it34 := (SELECT id FROM wms_item WHERE item_name='电磁水阀' AND item_category=@c34 AND item_brand=@b34 LIMIT 1);
SET @s34_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it34 AND sku_name='Q23XD-2L 220V 全铜' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'Q23XD-2L 220V 全铜', @it34, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s34_1 IS NULL;  -- #36 IMG_3088 数量1个

SET @c35 := (SELECT id FROM wms_item_category WHERE category_name='电磁水阀' LIMIT 1);
SET @b35 := (SELECT id FROM wms_item_brand WHERE brand_name='神州' LIMIT 1);
SET @it35 := (SELECT id FROM wms_item WHERE item_name='电磁水阀' AND item_category=@c35 AND item_brand=@b35 LIMIT 1);
SET @s35_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it35 AND sku_name='US-25 220V' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'US-25 220V', @it35, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s35_1 IS NULL;  -- #42 IMG_3089 数量29个

SET @c36 := (SELECT id FROM wms_item_category WHERE category_name='电磁水阀' LIMIT 1);
SET @b36 := (SELECT id FROM wms_item_brand WHERE brand_name='神州' LIMIT 1);
SET @it36 := (SELECT id FROM wms_item WHERE item_name='电磁水阀' AND item_category=@c36 AND item_brand=@b36 LIMIT 1);
SET @s36_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it36 AND sku_name='US-20 220V' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'US-20 220V', @it36, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s36_1 IS NULL;  -- #43 IMG_3089 数量13个

SET @c37 := (SELECT id FROM wms_item_category WHERE category_name='电磁水阀' LIMIT 1);
SET @b37 := (SELECT id FROM wms_item_brand WHERE brand_name='神州' LIMIT 1);
SET @it37 := (SELECT id FROM wms_item WHERE item_name='电磁水阀' AND item_category=@c37 AND item_brand=@b37 LIMIT 1);
SET @s37_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it37 AND sku_name='US-20K 220V 常开' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'US-20K 220V 常开', @it37, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s37_1 IS NULL;  -- #44 IMG_3089 数量9个

SET @c38 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b38 := (SELECT id FROM wms_item_brand WHERE brand_name='卓良(zholo)' LIMIT 1);
SET @it38 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c38 AND item_brand=@b38 LIMIT 1);
SET @s38_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it38 AND sku_name='MSV-86522R' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'MSV-86522R', @it38, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s38_1 IS NULL;  -- #70 IMG_3091 数量1个

SET @c39 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b39 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it39 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c39 AND item_brand=@b39 LIMIT 1);
SET @s39_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it39 AND sku_name='SD250612' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'SD250612', @it39, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s39_1 IS NULL;  -- #71 IMG_3091 数量13个

SET @c40 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b40 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it40 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c40 AND item_brand=@b40 LIMIT 1);
SET @s40_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it40 AND sku_name='S230805A' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'S230805A', @it40, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s40_1 IS NULL;  -- #72 IMG_3091 数量9个

SET @c41 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b41 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it41 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c41 AND item_brand=@b41 LIMIT 1);
SET @s41_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it41 AND sku_name='JM-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'JM-02', @it41, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s41_1 IS NULL;  -- #73 IMG_3091 数量4个

SET @c42 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b42 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it42 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c42 AND item_brand=@b42 LIMIT 1);
SET @s42_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it42 AND sku_name='MOV-02' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'MOV-02', @it42, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s42_1 IS NULL;  -- #74 IMG_3091 数量30个

SET @c43 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b43 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it43 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c43 AND item_brand=@b43 LIMIT 1);
SET @s43_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it43 AND sku_name='JM-01' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'JM-01', @it43, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s43_1 IS NULL;  -- #75 IMG_3091 数量12个

SET @c44 := (SELECT id FROM wms_item_category WHERE category_name='手动阀' LIMIT 1);
SET @b44 := (SELECT id FROM wms_item_brand WHERE brand_name='盛达(SDPC)' LIMIT 1);
SET @it44 := (SELECT id FROM wms_item WHERE item_name='气动手动阀' AND item_category=@c44 AND item_brand=@b44 LIMIT 1);
SET @s44_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it44 AND sku_name='S230805' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'S230805', @it44, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s44_1 IS NULL;  -- #76 IMG_3091 数量16个

SET @c45 := (SELECT id FROM wms_item_category WHERE category_name='气源处理类' LIMIT 1);
SET @it45 := (SELECT id FROM wms_item WHERE item_name='调压过滤器' AND item_category=@c45 AND item_brand IS NULL LIMIT 1);
SET @s45_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it45 AND sku_name='NAR2000' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'NAR2000', @it45, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s45_1 IS NULL;  -- #94 IMG_3092 数量39个

SET @c46 := (SELECT id FROM wms_item_category WHERE category_name='气动电磁阀' LIMIT 1);
SET @b46 := (SELECT id FROM wms_item_brand WHERE brand_name='ZLPC' LIMIT 1);
SET @it46 := (SELECT id FROM wms_item WHERE item_name='气动电磁阀' AND item_category=@c46 AND item_brand=@b46 LIMIT 1);
SET @s46_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it46 AND sku_name='Q22HD-15' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'Q22HD-15', @it46, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s46_1 IS NULL;  -- #103 IMG_3092 数量9个

SET @c47 := (SELECT id FROM wms_item_category WHERE category_name='不锈钢接头' LIMIT 1);
SET @it47 := (SELECT id FROM wms_item WHERE item_name='不锈钢接头' AND item_category=@c47 AND item_brand IS NULL LIMIT 1);
SET @s47_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it47 AND sku_name='6分套管' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '6分套管', @it47, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s47_1 IS NULL;  -- #117 IMG_3093 数量11个

SET @c48 := (SELECT id FROM wms_item_category WHERE category_name='不锈钢接头' LIMIT 1);
SET @it48 := (SELECT id FROM wms_item WHERE item_name='不锈钢接头' AND item_category=@c48 AND item_brand IS NULL LIMIT 1);
SET @s48_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it48 AND sku_name='4分' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '4分', @it48, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s48_1 IS NULL;  -- #118 IMG_3093 数量1个

SET @c49 := (SELECT id FROM wms_item_category WHERE category_name='油压表' LIMIT 1);
SET @it49 := (SELECT id FROM wms_item WHERE item_name='油压表' AND item_category=@c49 AND item_brand IS NULL LIMIT 1);
SET @s49_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it49 AND sku_name='50kg' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '50kg', @it49, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s49_1 IS NULL;  -- #138 IMG_3094 数量6个

SET @c50 := (SELECT id FROM wms_item_category WHERE category_name='油压表' LIMIT 1);
SET @it50 := (SELECT id FROM wms_item WHERE item_name='油压表' AND item_category=@c50 AND item_brand IS NULL LIMIT 1);
SET @s50_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it50 AND sku_name='40kg' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '40kg', @it50, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s50_1 IS NULL;  -- #139 IMG_3094 数量1个

SET @c51 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it51 := (SELECT id FROM wms_item WHERE item_name='宝塔直通' AND item_category=@c51 AND item_brand IS NULL LIMIT 1);
SET @s51_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it51 AND sku_name='8 双头' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '8 双头', @it51, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s51_1 IS NULL;  -- #148 IMG_3095 数量500个

SET @c52 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it52 := (SELECT id FROM wms_item WHERE item_name='宝塔直通' AND item_category=@c52 AND item_brand IS NULL LIMIT 1);
SET @s52_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it52 AND sku_name='8-6' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '8-6', @it52, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s52_1 IS NULL;  -- #149 IMG_3095 数量600个

SET @c53 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it53 := (SELECT id FROM wms_item WHERE item_name='宝塔直通' AND item_category=@c53 AND item_brand IS NULL LIMIT 1);
SET @s53_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it53 AND sku_name='8-10' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '8-10', @it53, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s53_1 IS NULL;  -- #150 IMG_3095 数量294个

SET @c54 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it54 := (SELECT id FROM wms_item WHERE item_name='宝塔直通' AND item_category=@c54 AND item_brand IS NULL LIMIT 1);
SET @s54_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it54 AND sku_name='10' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '10', @it54, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s54_1 IS NULL;  -- #151 IMG_3095 数量71个

SET @c55 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it55 := (SELECT id FROM wms_item WHERE item_name='宝塔直通' AND item_category=@c55 AND item_brand IS NULL LIMIT 1);
SET @s55_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it55 AND sku_name='2' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '2', @it55, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s55_1 IS NULL;  -- #156 IMG_3096 数量15个

SET @c56 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it56 := (SELECT id FROM wms_item WHERE item_name='宝塔直通' AND item_category=@c56 AND item_brand IS NULL LIMIT 1);
SET @s56_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it56 AND sku_name='16' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '16', @it56, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s56_1 IS NULL;  -- #157 IMG_3096 数量1个

SET @c57 := (SELECT id FROM wms_item_category WHERE category_name='宝塔接头' LIMIT 1);
SET @it57 := (SELECT id FROM wms_item WHERE item_name='宝塔直通' AND item_category=@c57 AND item_brand IS NULL LIMIT 1);
SET @s57_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it57 AND sku_name='6' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT '6', @it57, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s57_1 IS NULL;  -- #158 IMG_3096 数量76个

SET @c58 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it58 := (SELECT id FROM wms_item WHERE item_name='鱼眼接头' AND item_category=@c58 AND item_brand IS NULL LIMIT 1);
SET @s58_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it58 AND sku_name='M20X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M20X1.5', @it58, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s58_1 IS NULL;  -- #161 IMG_3096 数量29个

SET @c59 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it59 := (SELECT id FROM wms_item WHERE item_name='鱼眼接头' AND item_category=@c59 AND item_brand IS NULL LIMIT 1);
SET @s59_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it59 AND sku_name='M16X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M16X1.5', @it59, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s59_1 IS NULL;  -- #162 IMG_3096 数量15个

SET @c60 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it60 := (SELECT id FROM wms_item WHERE item_name='鱼眼接头' AND item_category=@c60 AND item_brand IS NULL LIMIT 1);
SET @s60_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it60 AND sku_name='M18X1.5' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M18X1.5', @it60, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s60_1 IS NULL;  -- #163 IMG_3096 数量1个

SET @c61 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it61 := (SELECT id FROM wms_item WHERE item_name='鱼眼接头' AND item_category=@c61 AND item_brand IS NULL LIMIT 1);
SET @s61_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it61 AND sku_name='M12X1.25' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M12X1.25', @it61, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s61_1 IS NULL;  -- #164 IMG_3096 数量37个

SET @c62 := (SELECT id FROM wms_item_category WHERE category_name='配件' LIMIT 1);
SET @it62 := (SELECT id FROM wms_item WHERE item_name='鱼眼接头' AND item_category=@c62 AND item_brand IS NULL LIMIT 1);
SET @s62_1 := (SELECT id FROM wms_item_sku WHERE item_id=@it62 AND sku_name='M10X1.25' LIMIT 1);
INSERT INTO wms_item_sku (sku_name, item_id, create_by, create_time, update_by, update_time)
  SELECT 'M10X1.25', @it62, @OP, @TS, @OP, @TS FROM (SELECT 1) _x WHERE @s62_1 IS NULL;  -- #165 IMG_3096 数量44个

COMMIT;

-- ============ 5. 核对 ============
SELECT '新建品牌' AS 项, COUNT(*) AS 条数 FROM wms_item_brand WHERE brand_name IN ('照庆')
UNION ALL SELECT '新建分类', COUNT(*) FROM wms_item_category WHERE category_name IN ('手指气缸','抽油泵','单向节流阀')
UNION ALL SELECT '本次创建的商品', COUNT(*) FROM wms_item WHERE create_time=@TS
UNION ALL SELECT '本次创建的SKU', COUNT(*) FROM wms_item_sku WHERE create_time=@TS;
