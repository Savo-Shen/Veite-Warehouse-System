# -*- coding: utf-8 -*-
"""生成「压油管并入主商品体系」的迁移 SQL。

从当前库里的 wms_hose_spec / wms_hose_fitting / wms_hose_ferrule 读出来，
生成 wms_item_category / wms_item / wms_item_sku / wms_inventory 的插入语句，
再把三张属性表瘦身（去掉 qty/库位/进价，加 sku_id）。

ID 用固定高位段，Mac 和 Windows 跑出来完全一样，可重复执行。
"""
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "..", "..", "backend", "script", "sql",
                                   "hose_merge_into_item.sql"))

USER = os.environ["MYSQL_USERNAME"]
WAREHOUSE_ID = 1945324849064845313          # 泉州市威特液压设备有限公司，全库唯一仓库
PARENT_CAT = 1945335704057815041            # 顶级分类「液压接头」，原本是空的

CAT_BASE = 1992000000000000001               # 三个二级分类
ITEM_BASE = 1992000000000000011              # 五个商品
SKU_BASE = 1992000000001000000               # SKU 从这里往后排


def q(sql):
    p = subprocess.run(
        ["mysql", "--default-character-set=utf8mb4", "-h", "127.0.0.1", "-u", USER,
         "ry-vue", "-N", "-B", "--raw", "-e", sql],
        capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError(p.stderr.strip())
    return [l.split("\t") for l in p.stdout.split("\n") if l]


def lit(v):
    if v is None or v == "NULL":
        return "NULL"
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def num(v):
    return "NULL" if v in (None, "", "NULL") else str(v)


# ------------------------------------------------------------
# 分类与商品
# ------------------------------------------------------------
CATS = [
    (CAT_BASE + 0, "液压胶管", 1),
    (CAT_BASE + 1, "胶管接头", 2),
    (CAT_BASE + 2, "扣压外套", 3),
]
ITEMS = [
    (ITEM_BASE + 0, "HOSE",     "液压胶管",     CAT_BASE + 0, "米", "按段存，分段明细见 wms_hose_piece"),
    (ITEM_BASE + 1, "HOSE-FM",  "公制胶管接头", CAT_BASE + 1, "个", "公制24°锥系列，螺纹 M14×1.5 ~ M22×1.5"),
    (ITEM_BASE + 2, "HOSE-FG",  "英制胶管接头", CAT_BASE + 1, "个", "英制 BSP G 螺纹系列"),
    (ITEM_BASE + 3, "HOSE-FJ",  "美制胶管接头", CAT_BASE + 1, "个", "美制 JIC 74°锥系列"),
    (ITEM_BASE + 4, "HOSE-FER", "扣压外套",     CAT_BASE + 2, "个", "皮子，三层管外径不同不通用"),
]
SYS_ITEM = {"公制": ITEM_BASE + 1, "英制": ITEM_BASE + 2, "美制": ITEM_BASE + 3}

out = []
sku_rows = []      # (sku_id, item_id, sku_name, sku_code, cost_price, location_id)
inv_rows = []      # (sku_id, quantity)
link = {"spec": [], "fitting": [], "ferrule": []}   # (业务主键, sku_id)
n = 0

# ---- 胶管 ----
hose = q("""SELECT hose_code, nickname, inch, layer_name, cost_price
              FROM wms_hose_spec ORDER BY CAST(bore_code AS UNSIGNED), layer_code""")
stock = {r[0]: r[1] for r in q(
    "SELECT hose_code, SUM(length_m) FROM wms_hose_piece WHERE status='在库' GROUP BY hose_code")}
loc = q("SELECT DISTINCT location_id FROM wms_hose_piece WHERE location_id IS NOT NULL LIMIT 1")
hose_loc = loc[0][0] if loc else None
for code, nick, inch, layer, cost in hose:
    n += 1
    sid = SKU_BASE + n
    sku_rows.append((sid, ITEM_BASE + 0, f"{nick} {inch} {layer}", code, cost,
                     hose_loc if code in stock else None))
    link["spec"].append((code, sid))
    if code in stock:
        inv_rows.append((sid, stock[code]))

# ---- 接头 ----
for sku, name, sysm, cost, locid in q(
        """SELECT fitting_sku, field_name, thread_system, cost_price, location_id
             FROM wms_hose_fitting
            ORDER BY FIELD(thread_system,'公制','英制','美制'), thread_spec, seat_type, gender, angle"""):
    n += 1
    sid = SKU_BASE + n
    sku_rows.append((sid, SYS_ITEM[sysm], name, sku, cost, locid))
    link["fitting"].append((sku, sid))

# ---- 外套 ----
for sku, name, cost, locid in q(
        """SELECT ferrule_sku, ferrule_name, cost_price, location_id FROM wms_hose_ferrule
            ORDER BY FIELD(layer_scope,'1层/2层','3层','4层','6层'), CAST(bore_code AS UNSIGNED), skin_type"""):
    n += 1
    sid = SKU_BASE + n
    sku_rows.append((sid, ITEM_BASE + 4, name, sku, cost, locid))
    link["ferrule"].append((sku, sid))

# ------------------------------------------------------------
# 拼 SQL
# ------------------------------------------------------------
out.append(f"""-- ============================================================
-- 压油管并入主商品体系
--
-- 原来 wms_hose_fitting/ferrule 自带 qty、location_id、cost_price，跟
-- wms_item/wms_inventory 完全平行 —— 结果是接头没法单卖、没法走入库单、
-- 盘点单盘不到、库存统计看不见、改了库存也不留痕。液压店单卖接头是日常生意，
-- 这个洞不能留。
--
-- 并入后：
--   接头/外套/胶管的「库存、库位、进价、售价」全部回到 wms_item_sku +
--   wms_inventory，进销存走既有流程；
--   wms_hose_spec/fitting/ferrule 只保留业务属性（现场叫法、螺纹、A/C/D型、
--   芯面、直弯、通径、层数），靠 sku_id 挂在 SKU 上，专供配料页筛选；
--   wms_hose_piece 降级为扩展表，只回答「这些米数是怎么分段的」。
--
-- 为什么胶管仍需要 wms_hose_piece：wms_inventory 是「一 SKU 一个数量」，
-- 1602 合计 32 米实际分 10+10+4+8 四段，quantity=32 装不下这个信息，
-- 接 12 米的单子会误判成够。分段表和 inventory.quantity 的加总必须一致。
--
-- !! 本文件由 output/压油管/gen_merge_sql.py 生成，不要手改 !!
-- 幂等：固定 ID + INSERT IGNORE；ALTER 走 information_schema 判断。
-- 依赖：hose_module.sql、hose_module_seed.sql
-- 回滚：hose_merge_into_item_rollback.sql
-- ============================================================

SET NAMES utf8mb4;


-- ------------------------------------------------------------
-- ① 分类：挂在原本空着的顶级分类「液压接头」下面
-- ------------------------------------------------------------""")
out.append("INSERT IGNORE INTO `wms_item_category` (id,parent_id,category_name,order_num,status) VALUES")
out.append(",\n".join(f"  ({i},{PARENT_CAT},{lit(nm)},{o},'1')" for i, nm, o in CATS) + ";\n")

out.append("-- ② 商品")
out.append("INSERT IGNORE INTO `wms_item` (id,item_code,item_name,item_category,unit,remark) VALUES")
out.append(",\n".join(f"  ({i},{lit(c)},{lit(nm)},{cat},{lit(u)},{lit(rm)})"
                      for i, c, nm, cat, u, rm in ITEMS) + ";\n")

out.append(f"-- ③ SKU：{len(sku_rows)} 条（胶管 {len(link['spec'])} / 接头 {len(link['fitting'])} / 外套 {len(link['ferrule'])}）")
for i in range(0, len(sku_rows), 150):
    chunk = sku_rows[i:i + 150]
    out.append("INSERT IGNORE INTO `wms_item_sku` (id,item_id,sku_name,sku_code,cost_price,item_location_id) VALUES")
    out.append(",\n".join(f"  ({s},{it},{lit(nm)},{lit(cd)},{num(cp)},{num(lc)})"
                          for s, it, nm, cd, cp, lc in chunk) + ";\n")

out.append(f"""-- ④ 库存：只给盘过的建行。接头/外套 qty 是 NULL（还没盘），
--    按库里惯例「没库存就没有 wms_inventory 行」，不建 0 行 —— 0 是「盘过确认没有」。""")
if inv_rows:
    out.append("INSERT IGNORE INTO `wms_inventory` (sku_id,warehouse_id,quantity) VALUES")
    out.append(",\n".join(f"  ({s},{WAREHOUSE_ID},{v})" for s, v in inv_rows) + ";\n")

out.append("""-- ------------------------------------------------------------
-- ⑤ 三张属性表瘦身：加 sku_id，去掉 qty / location_id / cost_price
-- ------------------------------------------------------------""")


def add_col(table, col, ddl):
    return f"""SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='{table}' AND COLUMN_NAME='{col}'),
  'SELECT ''{table}.{col} 已存在''', '{ddl}');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;"""


def drop_col(table, col):
    return f"""SET @c = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='{table}' AND COLUMN_NAME='{col}'),
  'ALTER TABLE `{table}` DROP COLUMN `{col}`', 'SELECT ''{table}.{col} 已删除''');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;"""


for t, key in (("wms_hose_spec", "hose_code"), ("wms_hose_fitting", "fitting_sku"),
               ("wms_hose_ferrule", "ferrule_sku"), ("wms_hose_piece", "hose_code")):
    out.append(add_col(t, "sku_id",
        f"ALTER TABLE `{t}` ADD COLUMN `sku_id` bigint NULL DEFAULT NULL COMMENT ''关联 wms_item_sku.id'' AFTER `id`"))

out.append("\n-- 回填 sku_id")
for tbl, key, pairs in (("wms_hose_spec", "hose_code", link["spec"]),
                        ("wms_hose_fitting", "fitting_sku", link["fitting"]),
                        ("wms_hose_ferrule", "ferrule_sku", link["ferrule"])):
    cases = " ".join(f"WHEN {lit(k)} THEN {v}" for k, v in pairs)
    out.append(f"UPDATE `{tbl}` SET `sku_id` = CASE `{key}` {cases} END WHERE `sku_id` IS NULL;")
cases = " ".join(f"WHEN {lit(k)} THEN {v}" for k, v in link["spec"])
out.append(f"UPDATE `wms_hose_piece` SET `sku_id` = CASE `hose_code` {cases} END WHERE `sku_id` IS NULL;\n")

out.append("-- 去掉搬走的列")
for t, cols in (("wms_hose_spec", ["cost_price"]),
                ("wms_hose_fitting", ["qty", "location_id", "cost_price", "brand"]),
                ("wms_hose_ferrule", ["qty", "location_id", "cost_price"])):
    for c in cols:
        out.append(drop_col(t, c))

out.append("""
-- ------------------------------------------------------------
-- ⑥ 核对
-- ------------------------------------------------------------
SELECT '新建商品' AS 项, COUNT(*) AS 数量 FROM wms_item WHERE item_category IN
  (SELECT id FROM wms_item_category WHERE parent_id = %d)
UNION ALL SELECT '新建SKU', COUNT(*) FROM wms_item_sku WHERE item_id BETWEEN %d AND %d
UNION ALL SELECT '建了库存行', COUNT(*) FROM wms_inventory WHERE sku_id >= %d
UNION ALL SELECT '胶管属性未挂SKU', COUNT(*) FROM wms_hose_spec WHERE sku_id IS NULL
UNION ALL SELECT '接头属性未挂SKU', COUNT(*) FROM wms_hose_fitting WHERE sku_id IS NULL
UNION ALL SELECT '外套属性未挂SKU', COUNT(*) FROM wms_hose_ferrule WHERE sku_id IS NULL
UNION ALL SELECT '胶管分段未挂SKU', COUNT(*) FROM wms_hose_piece WHERE sku_id IS NULL;

-- 分段加总必须等于库存数量，不等说明有人只改了一边
SELECT p.sku_id, SUM(p.length_m) AS 分段合计, i.quantity AS 库存数量
  FROM wms_hose_piece p JOIN wms_inventory i ON i.sku_id = p.sku_id
 WHERE p.status = '在库' GROUP BY p.sku_id, i.quantity
HAVING SUM(p.length_m) <> i.quantity;
""" % (PARENT_CAT, ITEM_BASE, ITEM_BASE + 4, SKU_BASE))

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")
print("saved:", OUT)
print(f"  分类 {len(CATS)} / 商品 {len(ITEMS)} / SKU {len(sku_rows)} / 库存行 {len(inv_rows)}")
