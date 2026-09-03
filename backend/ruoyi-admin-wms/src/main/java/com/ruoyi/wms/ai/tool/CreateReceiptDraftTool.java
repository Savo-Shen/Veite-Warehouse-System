package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.wms.ai.tool.DraftSupport.Draft;
import com.ruoyi.wms.ai.tool.DraftSupport.DraftLine;
import com.ruoyi.wms.ai.tool.DraftSupport.SkuPick;
import com.ruoyi.wms.ai.tool.DraftSupport.SourceLine;
import com.ruoyi.wms.ai.tool.DraftSupport.SourceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：根据自然语言生成「入库单草稿」。
 * <p>
 * 只返回可预填到现有入库编辑页的结构化草稿，不直接写库。支持复制已有单据、指定单价模式。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class CreateReceiptDraftTool implements AiTool {

    private final DraftSupport support;

    @Override
    public String requiredPermission() {
        return DraftSupport.PERM_RECEIPT;
    }

    @Override
    public List<String> permissionsUsed() {
        return List.of(DraftSupport.PERM_RECEIPT, DraftSupport.PERM_SHIPMENT);
    }

    @Override
    public String name() {
        return "create_receipt_draft";
    }

    @Override
    public String title() {
        return "正在生成入库草稿";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "生成入库单草稿（不会真正入库/保存，前端会预填到入库单表单交由用户确认）。"
            + "当用户想从某供应商入库、采购入库、生产入库、退货入库、补库存、进货、到货或收货时调用。"
            + "支持：① 复制已有单据（copyFromOrderNo，入库单或出库单都行，例如把一张出库单按进价转成入库单）；"
            + "② 指定单价模式 priceMode（进价/售价/来源单价/0）或每行明确单价 price；③ items 里已知 skuId 时直接传 skuId。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> item = object(props(
            "name", string("商品/规格关键字，如“PU管 10*6.5”。已知 skuId 时可省略"),
            "skuId", number("商品规格 id（可选），来自 search_sku/get_order 等结果，给了就精确定位"),
            "quantity", number("入库数量。遇到“一卷/一捆/一箱”时填 1，“两卷/2卷”填 2，单位写到 unit"),
            "unit", string("数量单位（可选），如卷、捆、米、箱、包、个。同一商品可能有按米和按卷两条记录，单位决定选哪条"),
            "price", number("该行单价（可选）。用户明确说了单价才填"),
            "remark", string("该行备注（可选）")
        ), List.of("quantity"));

        return object(props(
            "supplier", string("供应商名称或编号（可选，没有就留空）"),
            "warehouse", string("入库仓库名称（可选，留空则用默认仓库）"),
            "optType", string("入库类型（可选）：2=采购入库（默认），1=生产入库，3=退货入库，4=归还入库"),
            "bizOrderNo", string("业务单号/采购单号（可选）"),
            "copyFromOrderNo", string("要复制的已有单据单号（可选，如 RK08186356 或 CK08054357）。明细、仓库自动带入，items 可在此基础上增改"),
            "priceMode", enumOf("单价取法（可选）：cost 进价（新建时默认）；selling 售价；source 复制来源单上的单价（复制时默认）；zero 全部 0",
                "cost", "selling", "source", "zero"),
            "items", array("要入库的商品清单（复制单据时可省略，或只写要增改的行）", item),
            "bizDate", string("入库日期 yyyy-MM-dd（可选，默认今天）"),
            "remark", string("整单备注（可选）")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        Draft d = new Draft();
        d.setType("receipt");
        String priceMode = DraftSupport.normalizePriceMode(text(args, "priceMode"));
        if ("last".equals(priceMode)) {
            priceMode = "cost";
        }

        String copyFrom = text(args, "copyFromOrderNo");
        SourceOrder src = null;
        if (copyFrom != null) {
            src = support.loadOrder(copyFrom, null);
            if (src == null) {
                d.getWarnings().add("未找到单号“" + copyFrom + "”，没有复制到任何明细。可先用 query_orders 按供应商/日期查单号。");
            } else {
                d.setSourceOrderNo(src.getOrderNo());
                if (!src.isShipment()) {
                    d.setMerchantId(src.getMerchantId());
                    d.setMerchantName(src.getMerchantName());
                    if (src.getOptType() != null) {
                        d.setOptType(String.valueOf(src.getOptType()));
                    }
                    d.setBizOrderNo(src.getBizOrderNo());
                } else {
                    d.getWarnings().add("来源 " + src.getOrderNo() + " 是出库单，它的客户不会带成供应商，请手动选择供应商。");
                }
                d.setWarehouseId(src.getWarehouseId());
                d.setWarehouseName(src.getWarehouseName());
                d.setRemark("由" + (src.isShipment() ? "出库单" : "入库单") + src.getOrderNo() + "复制");
                if (priceMode == null) {
                    priceMode = "source";
                }
            }
        }

        support.resolveMerchant(d, text(args, "supplier"), 2, "供应商");
        support.resolveWarehouse(d, text(args, "warehouse"));
        String opt = text(args, "optType");
        if (opt != null) {
            d.setOptType(normalizeOptType(opt));
        }
        if (d.getOptType() == null) {
            d.setOptType("2");
        }
        String bizOrderNo = text(args, "bizOrderNo");
        if (bizOrderNo != null) {
            d.setBizOrderNo(bizOrderNo);
        }
        String bizDateText = text(args, "bizDate");
        if (bizDateText != null) {
            LocalDate bd = date(args, "bizDate");
            if (bd == null) {
                d.getWarnings().add("日期“" + bizDateText + "”无法识别，已按今天。");
            } else {
                d.setBizDate(fmt(bd));
            }
        }
        String remark = text(args, "remark");
        if (remark != null) {
            d.setRemark(d.getRemark() == null ? remark : remark + "；" + d.getRemark());
        }
        if (priceMode == null) {
            priceMode = "cost";
        }
        d.setPriceMode(priceMode);

        List<DraftLine> lines = new ArrayList<>();
        Map<Long, DraftLine> bySku = new LinkedHashMap<>();
        if (src != null) {
            for (SourceLine sl : src.getLines()) {
                if (sl.getSkuId() == null || sl.getItemSku() == null) {
                    d.getWarnings().add("来源单里“" + sl.getItemName() + "”没有关联到具体商品（纯记录行），未复制。");
                    continue;
                }
                DraftLine l = new DraftLine();
                l.setSkuId(sl.getSkuId());
                l.setItem(sl.getItem());
                l.setItemSku(sl.getItemSku());
                l.setQuantity(sl.getQuantity());
                l.setSourcePrice(sl.getPrice());
                l.setRemark(sl.getRemark());
                lines.add(l);
                bySku.put(sl.getSkuId(), l);
            }
        }
        List<String> unitNotes = new ArrayList<>();
        JsonNode itemsNode = args.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode it : itemsNode) {
                String name = text(it, "name");
                BigDecimal skuIdNum = decimal(it, "skuId");
                Long skuId = skuIdNum == null ? null : skuIdNum.longValue();
                BigDecimal qty = decimal(it, "quantity");
                String unit = text(it, "unit");
                BigDecimal price = decimal(it, "price");
                String lineRemark = text(it, "remark");
                if (name == null && skuId == null) {
                    continue;
                }
                SkuPick pick = support.resolveSku(skuId, name, unit);
                if (!pick.found()) {
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("name", name != null ? name : ("skuId " + skuId));
                    u.put("quantity", qty);
                    u.put("reason", pick.reason());
                    d.getUnresolved().add(u);
                    continue;
                }
                if (pick.warning() != null) {
                    d.getWarnings().add(pick.warning());
                }
                Long sid = pick.picked().getItemSku().getId();
                DraftLine l = bySku.get(sid);
                if (l == null) {
                    l = new DraftLine();
                    l.setSkuId(sid);
                    l.setItem(pick.picked().getItem());
                    l.setItemSku(pick.picked().getItemSku());
                    lines.add(l);
                    bySku.put(sid, l);
                    if (qty == null) {
                        d.getWarnings().add("“" + DraftSupport.shortName(pick.picked()) + "”没有给数量，先按 1 填，请确认。");
                        qty = BigDecimal.ONE;
                    }
                }
                if (qty != null) {
                    l.setQuantity(qty);
                }
                if (price != null) {
                    l.setExplicitPrice(price);
                }
                if (lineRemark != null) {
                    l.setRemark(lineRemark);
                }
                if (unit != null && qty != null) {
                    unitNotes.add((name != null ? name : DraftSupport.shortName(pick.picked())) + " "
                        + plain(qty).toPlainString() + unit);
                }
            }
        }
        if (lines.isEmpty() && d.getUnresolved().isEmpty() && src == null) {
            return "{\"error\":\"没有可建单的内容：items 为空且没有 copyFromOrderNo\"}";
        }

        List<String> noPrice = new ArrayList<>();
        for (DraftLine l : lines) {
            BigDecimal p = support.priceFor(l.getItemSku(), priceMode, l.getExplicitPrice(), l.getSourcePrice(), null, false);
            if (p == null) {
                noPrice.add(l.getItem() == null ? String.valueOf(l.getSkuId()) : l.getItem().getItemName()
                    + " " + (l.getItemSku() == null ? "" : l.getItemSku().getSkuName()));
            }
            l.setPrice(p);
        }
        if (!noPrice.isEmpty()) {
            d.getWarnings().add("以下商品没有登记" + DraftSupport.priceModeLabel(priceMode, false) + "，单价留空请手动填写："
                + String.join("、", noPrice.subList(0, Math.min(noPrice.size(), 10)))
                + (noPrice.size() > 10 ? "…等 " + noPrice.size() + " 项" : ""));
        }
        if (!unitNotes.isEmpty()) {
            String unitRemark = "AI识别数量单位：" + String.join("；", unitNotes);
            d.setRemark(d.getRemark() == null ? unitRemark : d.getRemark() + "；" + unitRemark);
        }
        d.setDetails(lines);
        support.finish(d);
        return support.toJson(d);
    }

    @Override
    public String summarizeForModel(String result) {
        return support.summarize(result);
    }

    private String normalizeOptType(String value) {
        return switch (value.trim()) {
            case "1", "生产入库", "生产" -> "1";
            case "3", "退货入库", "退货" -> "3";
            case "4", "归还入库", "归还" -> "4";
            default -> "2";
        };
    }
}
