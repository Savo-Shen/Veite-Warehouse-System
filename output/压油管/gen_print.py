# -*- coding: utf-8 -*-
"""生成压油管盘点用的打印表（A4，手写清点）"""
import os
from sizes import (BORE, BY_CODE, CODES, LAYERS, METRIC_L, METRIC_S, BSP, JIC, NPT,
                   HOSE_COUNT, HOSE_LOC, FERRULE_GROUPS)

HERE = os.path.dirname(os.path.abspath(__file__))

CSS = """
@page { size: __SIZE__; margin: 8mm 8mm 7mm 8mm; }
* { box-sizing: border-box; }
body { margin:0; font-family: "PingFang SC","Heiti SC","Microsoft YaHei",sans-serif;
       color:#000; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.page { page-break-after: always; }
.page:last-child { page-break-after: auto; }
h1 { font-size: 14pt; margin: 0 0 .8mm 0; letter-spacing: .5px; }
.meta { font-size: 8.5pt; display:flex; gap:9mm; margin-bottom:1.5mm;
        border-bottom:1px solid #000; padding-bottom:1.2mm; }
.meta b { font-weight:600; }
h2 { font-size: 10.5pt; margin: 2mm 0 1.2mm 0; padding-left:2mm; border-left:3px solid #000; }
table { border-collapse: collapse; width:100%; table-layout: fixed; }
th, td { border:.5pt solid #555; text-align:center; vertical-align:middle;
         font-size: 8pt; padding:0; }
thead th { background:#e8e8e8; font-weight:600; line-height:1.25; padding:1mm .5mm; }
thead th.grp { background:#d0d0d0; font-size:8.5pt; }
thead th.loc { background:#f6f6f6; font-weight:400; font-size:7.5pt; text-align:left;
               padding-left:2mm; height:6.5mm; }
tbody td { height:__ROWH__; }
tbody td.hd { background:#f4f4f4; font-weight:600; font-size:8.5pt; }
tbody td.sub { background:#fafafa; font-size:7.5pt; color:#333; }
tbody td.na { background:#e0e0e0; }
tbody td.ro { background:#f0f6f0; font-size:8pt; }
tbody tr:nth-child(even) td:not(.hd):not(.sub):not(.na):not(.ro) { background:#fbfbfb; }
.note { font-size:7.5pt; margin-top:1.2mm; line-height:1.45; }
.note b { font-weight:600; }
.blank td { height:7mm; }
"""


def head(size, rowh):
    return ("<!doctype html><html><head><meta charset='utf-8'><style>"
            + CSS.replace("__SIZE__", size).replace("__ROWH__", rowh)
            + "</style></head><body>")


def meta_bar(title, extra=""):
    return (f"<h1>{title}</h1><div class='meta'>"
            "<span><b>盘点人：</b>____________</span>"
            "<span><b>日期：</b>_______年____月____日</span>"
            "<span><b>仓库：</b>泉州威特液压</span>"
            f"<span>{extra}</span></div>")


def bore_cell(b):
    """左侧通径单元格：内径码 + 俗称 + 英寸"""
    return (f"<td class='hd'>{b[0]} · {b[2]}<br>"
            f"<span style='font-weight:400;font-size:7pt'>{b[1]}\"</span></td>")


# ============================================================
# 表一：扣压外套（待盘） + 已盘胶管一览
# ============================================================
def sheet_ferrule():
    h = [head("A4 portrait", "8.6mm"), "<div class='page'>"]
    h.append(meta_bar("压油管盘点表（一） · 扣压外套 / 皮子",
                      "<span>第 1 页 / 共 1 页</span>"))

    h.append("<h2>1. 扣压外套 / 皮子（单位：个）</h2>")
    ndata = sum(2 if g[4] else 1 for g in FERRULE_GROUPS)
    dw = (100 - 13 - 12 - 17) / ndata
    h.append("<table><colgroup><col style='width:13%'>"
             + f"<col style='width:{dw}%'>" * ndata +
             "<col style='width:12%'><col style='width:17%'></colgroup><thead>")
    h.append("<tr><th rowspan='2'>通径<br>内径码·俗称</th>")
    for fcode, fname, layer, codes, split, hot in FERRULE_GROUPS:
        if split:
            h.append(f"<th colspan='2' class='grp'>{fname}</th>")
        else:
            h.append(f"<th rowspan='2'>{fname.replace('管用', '<br>管用')}</th>")
    h.append("<th rowspan='2'>库位</th><th rowspan='2'>厂家代号 / 备注</th></tr>")
    h.append("<tr>")
    for fcode, fname, layer, codes, split, hot in FERRULE_GROUPS:
        if split:
            h.append("<th>非剥皮</th><th>剥皮</th>")
    h.append("</tr></thead><tbody>")
    for b in BORE:
        cells = []
        for fcode, fname, layer, codes, split, hot in FERRULE_GROUPS:
            n = 2 if split else 1
            cells.append(("<td class='na'></td>" if b[0] not in codes else "<td></td>") * n)
        h.append("<tr>" + bore_cell(b) + "".join(cells) + "<td></td><td></td></tr>")
    h.append("</tbody></table>")
    h.append("<div class='note'><b>剥皮 / 非剥皮怎么分：</b>外套内壁有一圈台阶、装管前要把胶管外胶剥掉一段的是「剥皮式」；"
             "内壁直接带牙、整根管塞进去就压的是「非剥皮式」。分不清就都记在「非剥皮」栏并在备注写「不确定」。"
             "三层和六层管的外套一般不分剥皮，只有一栏。<b>灰格</b>是该通径一般不做的，跳过。<br>"
             "<b>三层这一档别漏：</b>库里 2503（一寸三层）有 20 米，三层管外径跟一二层不一样，外套不能通用。</div>")

    # --- 已盘胶管一览（只读，压管时对照用）---
    total_m = sum(sum(v) for v in HOSE_COUNT.values())
    total_p = sum(len(v) for v in HOSE_COUNT.values())
    h.append(f"<h2>2. 已盘胶管一览（{len(HOSE_COUNT)} 个规格 / {total_p} 段 / 合计 {total_m} 米，"
             f"全在 {HOSE_LOC}）— 不用再盘，压管时对照用</h2>")
    h.append("<table><colgroup><col style='width:10%'><col style='width:20%'>"
             "<col style='width:10%'><col style='width:12%'>"
             "<col style='width:10%'><col style='width:20%'>"
             "<col style='width:8%'><col style='width:10%'></colgroup><thead><tr>"
             "<th>代号</th><th>规格</th><th>合计<br>米</th><th>分段(米)</th>"
             "<th>代号</th><th>规格</th><th>合计<br>米</th><th>分段(米)</th>"
             "</tr></thead><tbody>")
    lay = {l[0]: l[1] for l in LAYERS}
    items = []
    for code in sorted(HOSE_COUNT, key=lambda c: (CODES.index(c[:2]), c[2:])):
        b = BY_CODE[code[:2]]
        p = HOSE_COUNT[code]
        items.append((code, f'{b[2]} {b[1]}" {lay[code[2:]]}', sum(p),
                      "+".join(str(x) for x in p)))
    half = (len(items) + 1) // 2
    for i in range(half):
        row = ["<tr>"]
        for it in (items[i], items[i + half] if i + half < len(items) else None):
            if it:
                row.append(f"<td class='ro'><b>{it[0]}</b></td><td class='ro'>{it[1]}</td>"
                           f"<td class='ro'>{it[2]}</td><td class='ro'>{it[3]}</td>")
            else:
                row.append("<td class='ro'></td>" * 4)
        h.append("".join(row) + "</tr>")
    h.append("</tbody></table>")
    h.append("<div class='note'><b>注意分段：</b>合计够不代表能用。例如 1602 合计 32 米，"
             "但分成 10+10+4+8 四段，做一根 12 米的就得另外进货。</div>")

    h.append("<h2>3. 表上没有的（自己补写）</h2>")
    h.append("<table><colgroup><col style='width:34%'><col style='width:12%'>"
             "<col style='width:12%'><col style='width:14%'><col style='width:14%'>"
             "<col style='width:14%'></colgroup><thead><tr>"
             "<th>名称 / 规格</th><th>通径</th><th>数量</th><th>单位</th>"
             "<th>库位</th><th>厂家代号</th></tr></thead><tbody>")
    for _ in range(3):
        h.append("<tr class='blank'>" + "<td></td>" * 6 + "</tr>")
    h.append("</tbody></table>")
    h.append("</div></body></html>")
    return "".join(h)


# ============================================================
# 表二：接头
# ============================================================
def fitting_table(groups):
    """groups: [(组名, 螺纹表, [子列名...], 是否显示螺纹列), ...]"""
    total_data = sum(len(g[2]) for g in groups)
    n_thread = sum(1 for g in groups if g[3])
    bore_w, thread_w = 9.0, 7.0
    data_w = (100 - bore_w - thread_w * n_thread) / total_data

    h = ["<table><colgroup>", f"<col style='width:{bore_w}%'>"]
    for gname, thmap, subs, show in groups:
        if show:
            h.append(f"<col style='width:{thread_w}%'>")
        h.append(f"<col style='width:{data_w}%'>" * len(subs))
    h.append("</colgroup><thead>")

    h.append("<tr><th rowspan='3'>通径<br>内径码·俗称</th>")
    for gname, thmap, subs, show in groups:
        h.append(f"<th colspan='{len(subs) + (1 if show else 0)}' class='grp'>{gname}</th>")
    h.append("</tr><tr>")
    for gname, thmap, subs, show in groups:
        h.append(f"<th colspan='{len(subs) + (1 if show else 0)}' class='loc'>库位：____________</th>")
    h.append("</tr><tr>")
    for gname, thmap, subs, show in groups:
        if show:
            h.append("<th>螺纹</th>")
        for s in subs:
            h.append(f"<th>{s}</th>")
    h.append("</tr></thead><tbody>")

    for b in BORE:
        h.append("<tr>" + bore_cell(b))
        for gname, thmap, subs, show in groups:
            th = thmap.get(b[0], "")
            na = (th == "—")
            if show:
                h.append(f"<td class='sub'>{th}</td>")
            for s in subs:
                h.append("<td class='na'></td>" if na else "<td></td>")
        h.append("</tr>")
    h.append("</tbody></table>")
    return "".join(h)


ANG = ["直", "45°", "90°"]


def sheet_fitting():
    h = [head("A4 landscape", "9.6mm")]

    h.append("<div class='page'>")
    h.append(meta_bar("压油管盘点表（二-1） · 胶管接头｜公制 24°锥",
                      "<span>第 1 页 / 共 2 页</span>"))
    h.append(fitting_table([
        ("轻系列 L · 内螺纹（母）", METRIC_L, ANG, True),
        ("轻系列 L · 外螺纹（公）", METRIC_L, ANG, False),
        ("重系列 S · 内螺纹（母）", METRIC_S, ANG, True),
        ("重系列 S · 外螺纹（公）", METRIC_S, ANG, False),
    ]))
    h.append("<div class='note'>"
             "<b>怎么认：</b>接头里面是 24° 锥面、螺纹是 M 开头的公制细牙 —— 国内工程机械上量最大的一种。"
             "<b>轻(L) / 重(S) 怎么分：</b>同一个通径，重系列螺纹比轻系列大一号（如四分管：轻 M22×1.5、重 M24×1.5），"
             "拿卡尺量外螺纹大径最快。<br>"
             "<b>内牙 / 外牙：</b>接头端面是母口（螺纹在里面）为内螺纹，是公口（螺纹在外面）为外螺纹。"
             "<b>灰格</b>表示该通径这个系列没有标准件。<b>22（7/8\"）</b>的螺纹栏空着是因为它不在欧标序列里，"
             "有货的话把实际螺纹写在空栏里。"
             "<b>库位</b>写在每组标题下的横线上；同组分了几个库位就在格子里注明。</div>")
    h.append("</div>")

    h.append("<div class='page'>")
    h.append(meta_bar("压油管盘点表（二-2） · 胶管接头｜英制 60°锥 / 美制 JIC / NPT",
                      "<span>第 2 页 / 共 2 页</span>"))
    h.append(fitting_table([
        ("英制 60°锥 · 内螺纹（母）", BSP, ANG, True),
        ("英制 60°锥 · 外螺纹（公）", BSP, ANG, False),
        ("美制 74° JIC · 内螺纹（母）", JIC, ANG, True),
        ("美制 74° JIC · 外螺纹（公）", JIC, ANG, False),
        ("NPT 锥管 · 外螺纹", NPT, ["直", "90°"], True),
    ]))
    h.append("<div class='note'>"
             "<b>60° 还是 74°：</b>英制 60°锥配 G 螺纹（G1/4、G1/2 这种），是国内第二大的一种；"
             "美制 JIC 是 74° 锥配英寸 UNF 牙（7/16-20 这种），多见于进口设备。两者锥角不同不能混用，"
             "分不清就量锥面角度或看螺纹是 G 还是分数英寸。"
             "NPT 是带锥度的管螺纹，拧上去会越拧越紧，一般只有外螺纹。</div>")

    h.append("<h2>其他接头（ORFS 平面密封 / 日标 30°锥 / SAE 法兰 / 表上没有的）</h2>")
    h.append("<table><colgroup><col style='width:26%'><col style='width:10%'>"
             "<col style='width:10%'><col style='width:14%'><col style='width:10%'>"
             "<col style='width:14%'><col style='width:16%'></colgroup><thead><tr>"
             "<th>名称 / 螺纹规格</th><th>通径</th><th>角度</th><th>厂家代号</th>"
             "<th>数量</th><th>库位</th><th>备注</th></tr></thead><tbody>")
    for _ in range(4):
        h.append("<tr class='blank'>" + "<td></td>" * 7 + "</tr>")
    h.append("</tbody></table>")
    h.append("</div></body></html>")
    return "".join(h)


for fn, html in [("盘点表1-扣压外套.html", sheet_ferrule()),
                 ("盘点表2-胶管接头.html", sheet_fitting())]:
    p = os.path.join(HERE, fn)
    with open(p, "w", encoding="utf-8") as f:
        f.write(html)
    print("wrote", p)
