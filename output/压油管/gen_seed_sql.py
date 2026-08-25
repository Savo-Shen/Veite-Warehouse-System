# -*- coding: utf-8 -*-
"""从 sizes.py / fittings.py 生成压油管模块的种子 SQL。

底稿 xlsx 和数据库用同一份规格定义，改规格只动 sizes.py / fittings.py，
然后重跑 gen_hose.py（出 xlsx）和本脚本（出 SQL），两边不会漂。
"""
import os

from sizes import (BORE, BY_CODE, CODES, LAYERS, HOSE_COUNT, HOSE_LOC,
                   FERRULE_GROUPS, price_of)
import fittings as FIT

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(
    HERE, "..", "..", "backend", "script", "sql", "hose_module_seed.sql"))

# 1-A1-3「油管」，2026-08-23 盘点时胶管全部在这个库位
HOSE_LOC_ID = 1958123026696409089

LAYER_NAME = {c: full for c, _s, full, _std in LAYERS}
LAYER_STD = {c: std for c, _s, _f, std in LAYERS}
# 胶管规格铺哪些层：现场只见过 1/2/3/4 层，纤维(00)和六层(06)一根没有，先不建
SEED_LAYERS = ["01", "02", "03", "04"]


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def rows_sql(table, cols, rows, chunk=200):
    out = []
    for i in range(0, len(rows), chunk):
        vals = ",\n  ".join("(" + ", ".join(q(v) for v in r) + ")" for r in rows[i:i + chunk])
        out.append(f"INSERT IGNORE INTO `{table}`\n  ({', '.join('`%s`' % c for c in cols)})\nVALUES\n  {vals};\n")
    return "\n".join(out)


# ------------------------------------------------------------
# ① 胶管规格
# ------------------------------------------------------------
spec_cols = ["hose_code", "bore_code", "layer_code", "inch", "nickname", "layer_name",
             "id_mm", "od_mm", "work_pressure_mpa", "bend_radius_mm",
             "cost_price", "price_source", "std_ref", "remark"]
spec_rows = []
for bcode, inch, nick, idmm, od1, od2, od4, p1, p2, p4, bend in BORE:
    for lcode in SEED_LAYERS:
        price, src = price_of(bcode, lcode)
        if price is None:
            continue                      # 06/08 不做 3/4 层缠绕管
        od = {"01": od1, "02": od2, "04": od4}.get(lcode)
        wp = {"01": p1, "02": p2, "04": p4}.get(lcode)
        note = None
        if bcode == "22":
            note = "22(7/8\")不在 EN 853/856 序列，外径/压力各厂不同，按实物补"
        elif lcode == "03":
            note = "三层管身直接印 3，不在欧标序列，无标准外径/压力"
        spec_rows.append([bcode + lcode, bcode, lcode, inch + '"', nick, LAYER_NAME[lcode],
                          idmm, od, wp, bend, price, src, LAYER_STD[lcode], note])

# ------------------------------------------------------------
# ② 胶管分段库存（2026-08-23 实盘）
# ------------------------------------------------------------
piece_cols = ["hose_code", "location_id", "length_m", "status", "remark"]
piece_rows = [[code, HOSE_LOC_ID, float(m), "在库", "2026-08-23 实盘"]
              for code in sorted(HOSE_COUNT) for m in HOSE_COUNT[code]]

# ------------------------------------------------------------
# ③ 接头（240 行，库存留 NULL = 还没盘）
# ------------------------------------------------------------
fit_cols = ["fitting_sku", "field_name", "thread_system", "thread_spec", "seat_type",
            "seal_std", "std_code", "gender", "angle", "bore_hint", "seen_on_sheet"]
fit_rows = []
for sys_, disp, tcode, thspec, bore, seen in FIT.THREADS:
    for seat, _how, sealstd, stdcode, _st in FIT.SEATS:
        for gcode, _gn, _gd in FIT.GENDERS:
            for bend, aname in FIT.ANGLES:
                fit_rows.append([
                    FIT.sku_of(tcode, seat, bend, gcode),
                    FIT.field_name(disp, seat, bend, gcode),
                    sys_, thspec, seat, sealstd, stdcode, gcode,
                    "弯" if bend else "直", bore, 1 if seen else 0,
                ])

# ------------------------------------------------------------
# ④ 扣压外套
# ------------------------------------------------------------
SKIN = [("非剥皮", "非剥皮式(整皮扣压)"), ("剥皮", "剥皮式(需剥外胶)")]
fer_cols = ["ferrule_sku", "ferrule_name", "layer_scope", "bore_code", "skin_type"]
fer_rows = []
for fcode, fname, layer, codes, split, _hot in FERRULE_GROUPS:
    skins = SKIN if split else [("不分", "")]
    for bcode in codes:
        for skcode, skname in skins:
            sku = f"{fcode}-{bcode}" + (f"-{skcode}" if skcode != "不分" else "")
            b = BY_CODE[bcode]
            fer_rows.append([sku,
                             f'扣压外套 {fname}{" " + skname if skname else ""} {b[2]} {b[1]}"',
                             layer, bcode, skcode])

# ------------------------------------------------------------
# ⑤ 扣压参数空行（现场实测后填）
# ------------------------------------------------------------
crimp_cols = ["layer_scope", "bore_code"]
crimp_rows = []
for _fc, _fn, layer, codes, _sp, _ho in FERRULE_GROUPS:
    for bcode in codes:
        crimp_rows.append([layer, bcode])

body = f"""-- ============================================================
-- 压油管模块种子数据
--
-- !! 本文件由 output/压油管/gen_seed_sql.py 生成，不要手改 !!
--    规格改动请动 output/压油管/sizes.py / fittings.py，然后重跑：
--      python gen_hose.py      # 出 xlsx 底稿
--      python gen_seed_sql.py  # 出本文件
--
-- 已有真实数据的只有胶管：2026-08-23 实盘 {len(HOSE_COUNT)} 个规格 /
-- {len(piece_rows)} 段 / 合计 {sum(sum(v) for v in HOSE_COUNT.values()):g} 米，全部在库位 {HOSE_LOC}。
-- 接头 {len(fit_rows)} 行、外套 {len(fer_rows)} 行的 qty 一律留 NULL —— NULL 是「还没盘」，
-- 0 是「盘过，确认没有」，页面上这两种要显示成不一样的东西，别在这里填 0。
-- 成本价同理，接头/外套一个都还没录，留 NULL。
--
-- 依赖：hose_module.sql（建表）
-- 幂等：全部 INSERT IGNORE，靠唯一键去重。胶管分段没有唯一键，
--       重复执行会重复插入，所以下面先按 remark 清一次 2026-08-23 的实盘行。
-- ============================================================

SET NAMES utf8mb4;


-- ① 胶管规格 {len(spec_rows)} 行
{rows_sql("wms_hose_spec", spec_cols, spec_rows)}

-- ② 胶管分段库存 {len(piece_rows)} 段（先删同批次再插，保证可重复执行）
DELETE FROM `wms_hose_piece` WHERE `remark` = '2026-08-23 实盘';
{rows_sql("wms_hose_piece", piece_cols, piece_rows)}

-- ③ 接头 {len(fit_rows)} 行
{rows_sql("wms_hose_fitting", fit_cols, fit_rows)}

-- ④ 扣压外套 {len(fer_rows)} 行
{rows_sql("wms_hose_ferrule", fer_cols, fer_rows)}

-- ⑤ 扣压参数占位 {len(crimp_rows)} 行
{rows_sql("wms_hose_crimp", crimp_cols, crimp_rows)}

SELECT '胶管规格' AS 表, COUNT(*) AS 行数 FROM wms_hose_spec
UNION ALL SELECT '胶管分段', COUNT(*) FROM wms_hose_piece
UNION ALL SELECT '接头',     COUNT(*) FROM wms_hose_fitting
UNION ALL SELECT '扣压外套', COUNT(*) FROM wms_hose_ferrule
UNION ALL SELECT '扣压参数', COUNT(*) FROM wms_hose_crimp;

SELECT SUM(length_m) AS 在库米数, COUNT(*) AS 段数 FROM wms_hose_piece WHERE status = '在库';
"""

with open(OUT, "w", encoding="utf-8") as f:
    f.write(body)
print("saved:", OUT)
print(f"  规格 {len(spec_rows)} / 分段 {len(piece_rows)} / 接头 {len(fit_rows)} / "
      f"外套 {len(fer_rows)} / 扣压参数 {len(crimp_rows)}")
