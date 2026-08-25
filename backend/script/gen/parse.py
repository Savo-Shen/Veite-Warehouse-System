# -*- coding: utf-8 -*-
"""按电商类目解析 SKU 名 → 规格参数 dict；并给出商品级属性。"""
import re
from kb import *

def _n(x):
    f = float(x)
    return int(f) if f == int(f) else f

def parse_sku(cat, prod, sku, brand):
    """返回 (spec dict, confidence)"""
    s = sku.strip(); S = s.upper(); s_raw = s
    P = prod.upper()

    # ---- 气缸 / 液压油缸：系列 + 缸径 * 行程 ----
    if cat.endswith("气缸") or cat.endswith("液压油缸"):
        m = re.search(r'([A-Z]{2,4})?\s*(\d+)\s*[\*xX×\-]\s*(\d+)', S)
        ser = None
        for k in sorted(CYL, key=len, reverse=True):
            if P.startswith(k) or S.startswith(k): ser = k; break
        if m:
            d = {"缸径": f"φ{_n(m.group(2))} mm", "行程": f"{_n(m.group(3))} mm"}
            if ser:
                t = CYL[ser]
                medium = "液压油" if cat.endswith("液压油缸") or ser == "MOB" else "空气"
                d.update({"系列": ser, "类型": t[0], "动作方式": t[1],
                          "材质": t[2], "使用压力": t[3], "使用温度": t[4], "使用介质": medium})
                d.update(t[6])          # 样本里的额外规格（耐压力/速度范围/缓冲型式等）
                # 接管口径与不回转精度按缸径查样本表
                bore = int(m.group(2))
                port = PORT.get(ser, {}).get(bore)
                if port:
                    d["接管口径"] = port
                if ser == "TN" and bore in TN_ROT:
                    d["不回转精度"] = TN_ROT[bore]
            if "SB" in S: d["附件"] = "含安装脚架(SB)"
            if "-S" in S or " S" in S: d["附件"] = "含磁性开关槽(S)"
            return d, "high" if ser else "medium"
        return ({"系列": ser} if ser else {}), "low"

    # ---- 气动接头：形态 + 管径 + 螺纹 ----
    if cat.endswith("气动接头") or cat.endswith("管件接头"):
        d = {"使用介质": "压缩空气 / 真空", "使用压力": "0~1.0 MPa",
             "使用温度": "-5~60 ℃", "材质": "本体黄铜镀镍，卡簧不锈钢，密封 NBR"}
        # 形态
        shape = None
        for k in sorted(FITTING_SHAPE, key=len, reverse=True):
            if P.startswith(k) or re.match(r'^D?'+k+r'\b', P): shape = FITTING_SHAPE[k]; break
        if shape: d["接头形态"] = shape
        # 全插管系列（PE/PY/PU/PG/PW/PK/PZA…）：形如 PE-04，横杠后是管径而非螺纹
        m = re.match(r'^(PE|PY|PU|PG|PW|PK|PZA|PEG|PV|PVL)\s*-\s*(\d{1,2})\s*$', S)
        if m:
            d["插管外径"] = f"φ{int(m.group(2))} mm"
            d["螺纹规格"] = "无（两端快插）"
            return d, "high"
        # 型号 管径-螺纹
        m = re.search(r'([A-Z]*)(\d+)\s*-\s*(M?\d+)', S)
        if m:
            d["插管外径"] = f"φ{_n(m.group(2))} mm"
            d["螺纹规格"] = thread_of(m.group(3))
            return d, "high"
        # 「快拧直通8-02」中文前缀
        m = re.search(r'(\d+)\s*-\s*(M?\d+)', s)
        if m:
            d["插管外径"] = f"φ{_n(m.group(1))} mm"; d["螺纹规格"] = thread_of(m.group(2))
            if "快拧" in s: d["接头形态"] = ("快拧" + ("直通" if "直通" in s else "弯头" if "弯头" in s else "三通" if "三通" in s else ""))
            return d, "high"
        m = re.search(r'(\d+)\s*分', s)
        if m: d["螺纹规格"] = FEN.get(m.group(1), m.group(1)+"分") + " (%s分)" % m.group(1); return d, "medium"
        m = re.search(r'(外丝|内丝|补芯|变径)\s*(\d)\s*\*\s*(\d)', s)   # 外丝4*2 = 4分转2分
        if m:
            d["类型"] = m.group(1) + "变径接头"
            d["接口规格"] = f"{FEN.get(m.group(2),'')} ({m.group(2)}分) 转 {FEN.get(m.group(3),'')} ({m.group(3)}分)"
            d["材质"] = "黄铜 / 镀锌钢"
            return d, "high"
        m = re.search(r'^(\d+)\s*\*\s*(\d+)', s)
        if m: d["规格"] = f"{_n(m.group(1))}×{_n(m.group(2))}"; return d, "medium"
        m = re.search(r'直径\s*(\d+)', s)
        if m: d["公称直径"] = f"DN{m.group(1)}"; return d, "medium"
        m = re.search(r'DN\s*(\d+)', S)
        if m: d["公称通径"] = "DN" + m.group(1); return d, "medium"
        m = re.search(r'(\d+)\s*寸', s)
        if m: d["接口规格"] = m.group(1) + "寸"; return d, "medium"
        # 纯插管件：名称里只有管径，两端都是快插，无螺纹
        m = re.search(r'(?:^|[A-Z\u4e00-\u9fa5])(\d{1,2})(?:\s*$|\s)', s)
        if m and 3 <= int(m.group(1)) <= 20:
            d["插管外径"] = f"φ{int(m.group(1))} mm"
            d["螺纹规格"] = "无（两端快插）"
            return d, "high"
        return d, "low"

    # ---- 气管 ----
    if cat.endswith("气管") or cat.endswith("冷却管"):
        d = {"使用介质": "压缩空气"}
        if "PU" in P: d.update({"材质": "聚氨酯 PU", "工作压力": "约 1.0 MPa（爆破约 3 倍）", "使用温度": "-20~60 ℃"})
        elif "PA" in P or "尼龙" in prod: d.update({"材质": "尼龙 PA", "使用温度": "-20~100 ℃"})
        elif "PE" in P: d.update({"材质": "聚乙烯 PE", "使用温度": "-20~60 ℃"})
        elif "PVC" in P: d.update({"材质": "PVC 增强软管"})
        m = re.search(r'(\d+(?:\.\d+)?)\s*\*\s*(\d+(?:\.\d+)?)', s)
        if m:
            a, b = _n(m.group(1)), _n(m.group(2))
            lo, hi = min(a, b), max(a, b)
            d["外径"] = f"φ{hi} mm"; d["内径"] = f"φ{lo} mm"
        for c in ("透明","红色","蓝色","黑色","白色","黄色","绿色"):
            if c in s: d["颜色"] = c
        m = re.search(r'(\d+)\s*M', S)
        if m: d["盘长"] = f"{m.group(1)} 米/卷"
        return d, "high" if "外径" in d else "medium"

    # ---- 气动电磁阀 / 气动控制阀 ----
    if "电磁阀" in cat or "控制阀" in cat:
        d = {"使用介质": "空气"}
        m = re.match(r'^(\d)V', S)
        if m:
            d["阀类型"] = {"2":"二位二通","3":"二位三通","4":"二位五通"}.get(m.group(1), "换向阀")
            d.update({"材质":"铝合金阀体","先导方式":"内部先导式","结构":"滑柱式",
                      "使用压力":"0.15~0.8 MPa"})
        m = re.search(r'-(\d{2})\b', S)
        if m and m.group(1) in V_PORT: d["接管口径"] = V_PORT[m.group(1)]
        m = re.search(r'(\d+)\s*V', S)
        if m: d["电压"] = ("AC" if m.group(1) in ("220","110","36","24") and "DC" not in S else "DC") + m.group(1) + "V"
        if "220" in S: d["电压"] = "AC220V"
        if "24V" in S and "220" not in S: d["电压"] = "DC24V"
        m = re.search(r'HV\s*-?\s*(\d+)', S)
        if m: d.update({"阀类型":"手转阀","接管口径": V_PORT.get(m.group(1)[-2:], m.group(1))})
        return d, "high" if len(d) > 3 else "medium"

    # ---- 电磁水阀 ----
    if cat.endswith("电磁水阀"):
        d = {"使用介质": "水 / 油 / 空气", "常态": "常闭（NC）"}
        if S.startswith("2W"): d.update({"系列":"2W 黄铜电磁阀","材质":"黄铜阀体，NBR 密封"})
        m = re.search(r'2W\s*-?(\d{3})(\d{2})', S)
        if m: d["公称通径"] = f"DN{int(m.group(2))}"; d["孔径"] = f"{int(m.group(1))/10:g} mm"
        m = re.search(r'(\d+)\s*V', S)
        if m: d["电压"] = ("AC" if m.group(1) in ("220","110") else "DC") + m.group(1) + "V"
        m = re.search(r'DN\s*(\d+)', S)
        if m: d["公称通径"] = "DN" + m.group(1)
        m = re.search(r'(\d+)\s*分', s)
        if m: d["接口"] = FEN.get(m.group(1), "") + f" ({m.group(1)}分)"
        return d, "medium"

    # ---- 气源处理器 ----
    if cat.endswith("气源处理器"):
        d = {"使用介质": "压缩空气", "材质": "本体铝合金，water杯 PC"}
        d["材质"] = "本体铝合金，水杯 PC（聚碳酸酯）"
        m = re.match(r'^([A-Z]{1,3})\s*-?\s*(\d{4})', S)
        if m:
            pre, size = m.group(1), m.group(2)
            if pre in FRL:
                d["类型"] = FRL[pre]
            port = FRL_PORT.get(size)
            if port:
                d["接管口径"] = port
            # 子系列共性规格：R=调压阀，FR=调压过滤，FC=二联件
            key = "FC" if pre.endswith("FC") else ("FR" if pre.endswith("FR") else
                  ("R" if pre.endswith("R") else None))
            if key:
                d.update(FRL_SPEC[key])
            # 样本重量——目前唯一有厂商公布重量的品类
            w = FRL_WEIGHT.get(pre)
            if w:
                d["样本重量"] = f"{w} g（亚德客官方样本，非实测；毛重另需称）"
            return d, "high" if ("类型" in d and port) else "medium"
        m = re.search(r'-(\d{2})\b', S)
        if m and m.group(1) in V_PORT:
            d["接管口径"] = V_PORT[m.group(1)]
        return d, "medium"

    # ---- 喉箍 ----
    if cat.endswith("喉箍"):
        d = {"材质": "不锈钢" if "不锈钢" in s else "镀锌钢"}
        if "304" in s_raw: d["材质"] = "不锈钢 304"
        elif "镀锌" in s_raw: d["材质"] = "镀锌钢"
        if "强力" in s_raw: d["类型"] = "强力型喉箍"
        m = re.search(r'(\d+)\s*-\s*(\d+)\s*mm', s)
        if m:
            d["夹持范围"] = f"{m.group(1)}~{m.group(2)} mm"; return d, "high"
        m = re.search(r'(\d+)\s*mm', s)
        if m: d["规格"] = f"{m.group(1)} mm"; return d, "high"
        return d, "low"

    # ---- 液压阀 / 液压泵 ----
    if cat.endswith("液压阀"):
        d = {"使用介质":"液压油","类型":"液压换向阀"}
        if S.startswith("DSG"): d.update({"系列":"DSG 电磁换向阀","额定压力":"31.5 MPa"})
        if S.startswith("4WE"): d.update({"系列":"4WE 电磁换向阀（力士乐型）","额定压力":"31.5 MPa"})
        if "MTCV" in S: d.update({"类型":"叠加式双向节流阀"})
        if "MTV"  in S: d.update({"类型":"叠加式节流阀"})
        m = re.search(r'-\s*0?(\d)\b', S)
        if m: d["通径"] = f"0{m.group(1)} ({'06' if m.group(1)=='2' else '10'} 通径系列)" if m.group(1) in "23" else m.group(1)
        m = re.search(r'\(?\s*(\d+)\s*V', S)
        if m: d["电压"] = ("AC" if m.group(1) in ("220","110") else "DC") + m.group(1) + "V"
        return d, "medium"
    if cat.endswith("液压泵"):
        d = {"使用介质":"液压油"}
        if S.startswith("CB-B") or S.startswith("CBB"): d.update({"类型":"齿轮泵","系列":"CB-B"})
        elif S.startswith("HGP"): d.update({"类型":"齿轮泵","系列":"HGP"})
        elif "CY" in S: d.update({"类型":"轴向柱塞泵","系列":"CY14-1B"})
        elif S.startswith("VP"): d.update({"类型":"叶片泵","系列":"VP"})
        m = re.search(r'(\d+)$', S)
        if m: d["排量档位"] = m.group(1)
        return d, "medium"

    # ---- 压力表 ----
    if cat.endswith("压力表"):
        d = {"类型":"压力表"}
        m = re.search(r'([\d.]+)\s*kg', s, re.I)
        if m: d["量程"] = f"0~{m.group(1)} kg/cm²"
        m = re.search(r'(\d+/\d+)\s*牙', s)
        if m: d["接口螺纹"] = m.group(1) + '"'
        return d, "high" if len(d) > 1 else "low"

    # ---- 球阀 ----
    if cat.endswith("球阀"):
        d = {"类型":"球阀","使用介质":"水 / 气 / 油"}
        m = re.search(r'(\d+)\s*分', s)
        if m: d["接口"] = FEN.get(m.group(1),"") + f" ({m.group(1)}分)"
        m = re.search(r'插\s*(\d+)', s)
        if m: d["快插管径"] = f"φ{m.group(1)} mm"
        return d, "medium"

    # ---- 气动工具（吹尘枪）----
    if cat.endswith("气动工具"):
        d = {"类型":"气动吹尘枪","使用介质":"压缩空气","使用压力":"0.3~0.8 MPa",
             "材质":"铝合金枪体 + 工程塑料","接口":'G1/4" (2分)'}
        for c in ("黄色","红色","蓝色","黑红","黑色"):
            if c in s_raw: d["颜色"] = c
        if "长" in s_raw: d["规格"] = "加长喷管"
        return d, "medium"

    # ---- 气缸配件 ----
    if cat.endswith("气缸配件"):
        d = {"类型":"气缸配件"}
        import re as _re
        if "RMS" in S or "磁性" in prod: d.update({"类型":"磁性开关","使用电压":"DC5~240V / AC5~240V","输出":"两线常开"})
        m = _re.search(r'M(\d+)\s*\*\s*(\d+)', S)
        if m: d.update({"类型":"气缸用连接件","螺纹":f"M{m.group(1)}","长度":f"{m.group(2)} mm"})
        m = _re.search(r'^(\d+)CA', S)
        if m: d.update({"类型":"气缸安装附件(CA 后耳环)","适用缸径":f"φ{m.group(1)} mm"})
        return d, "medium"

    # ---- 黄油嘴 ----
    if cat.endswith("黄油嘴"):
        d = {"类型":"直通式黄油嘴(油杯)","材质":"碳钢镀锌","使用介质":"润滑脂"}
        import re as _re
        m = _re.search(r'M(\d+)', S)
        if m: d["螺纹规格"] = f"M{m.group(1)}"
        return d, "high" if "螺纹规格" in d else "medium"

    # ---- 液压配件（滤网）----
    if cat.endswith("液压配件"):
        d = {"类型":"液压油箱吸油滤网","材质":"不锈钢滤网 + 金属骨架","使用介质":"液压油"}
        import re as _re
        m = _re.search(r'([一二三四五六七八九十\d]+)\s*寸', s_raw)
        if m: d["接口规格"] = m.group(1) + "寸"
        return d, "medium"

    return {}, "low"
