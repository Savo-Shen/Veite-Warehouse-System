package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 工具：对一张<b>还能改</b>的单据（未出库/未入库、或纯记录出库单）生成修改草稿：改数量、改单价、增删行、改备注。
 * <p>
 * 同样不落库：前端会打开该单据的编辑页并把改动预填进去，用户确认后才保存。
 * 已出库/已入库/作废的单据明细已锁定，这里会拒绝并提示改用复制。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class ModifyOrderDraftTool implements AiTool {

    private final DraftSupport support;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> permissionsUsed() {
        return List.of(DraftSupport.PERM_SHIPMENT, DraftSupport.PERM_RECEIPT);
    }

    @Override
    public String name() {
        return "modify_order_draft";
    }

    @Override
    public String title() {
        return "正在生成改单草稿";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "修改一张已有单据（出库单或入库单）的草稿：改某行数量/单价、整单换单价模式、增加或删除商品行、改备注。"
            + "只对“未出库/未入库”或“纯记录”的单据有效；已出库/已入库/作废的单据明细已锁定，工具会返回错误并说明，"
            + "这时应改用 create_shipment_draft/create_receipt_draft 的 copyFromOrderNo 复制成新单。"
            + "不会直接保存：前端会打开该单据的编辑页并预填改动，由用户确认。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> item = object(props(
            "skuId", number("要改的行的商品规格 id（可选，来自 get_order 的结果，最精确）"),
            "name", string("要改的行的商品名/规格关键字（没有 skuId 时用）；不在单上的商品会作为新行加入"),
            "quantity", number("新的数量（可选，不改就不填）"),
            "unit", string("新增行的数量单位（可选）"),
            "price", number("新的单价（可选，不改就不填）"),
            "remark", string("该行备注（可选）"),
            "remove", bool("true 表示删除这一行")
        ), List.of());

        return object(props(
            "orderNo", string("要修改的单号，如 CK10238365 或 RK08186356"),
            "priceMode", enumOf("整单单价取法（可选）：keep 保持原单价（默认）；cost 全部改成进价；selling 全部改成售价；"
                + "last 改成该客户上次成交价（仅出库单）；zero 全部 0。items 里明确给了 price 的行以 price 为准",
                "keep", "cost", "selling", "last", "zero"),
            "items", array("要增/改/删的行（可选，只写有变化的）", item),
            "remark", string("新的整单备注（可选）"),
            "bizDate", string("新的单据日期 yyyy-MM-dd（可选）")
        ), List.of("orderNo"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String orderNo = text(args, "orderNo");
        if (orderNo == null) {
            return "{\"error\":\"orderNo 不能为空\"}";
        }
        SourceOrder src = support.loadOrder(orderNo, null);
        if (src == null) {
            return error("未找到单号“" + orderNo + "”（或当前账号没有权限查看这类单据）",
                "可先用 query_orders 按客户/供应商/日期查到准确单号");
        }
        if (!src.isEditable()) {
            return error("单据 " + src.getOrderNo() + " " + src.editableReason(),
                "如果是想按新价格/数量再来一张，请调用 " + (src.isShipment() ? "create_shipment_draft" : "create_receipt_draft")
                    + " 并传 copyFromOrderNo=\"" + src.getOrderNo() + "\"（priceMode 可选 cost/selling/last）");
        }
        boolean shipment = src.isShipment();

        Draft d = new Draft();
        d.setType(src.getType());
        d.setMode("edit");
        d.setOrderId(src.getId());
        d.setOrderNo(src.getOrderNo());
        d.setMerchantId(src.getMerchantId());
        d.setMerchantName(src.getMerchantName());
        d.setWarehouseId(src.getWarehouseId());
        d.setWarehouseName(src.getWarehouseName());
        d.setOptType(src.getOptType() == null ? null : String.valueOf(src.getOptType()));
        d.setBizOrderNo(src.getBizOrderNo());
        d.setBizDate(fmt(src.getBizDate()));
        d.setRemark(src.getRemark());
        d.setRecordOnly(src.getRecordOnly());

        String priceMode = DraftSupport.normalizePriceMode(text(args, "priceMode"));
        if (priceMode == null) {
            priceMode = "keep";
        }
        if (!shipment && "last".equals(priceMode)) {
            d.getWarnings().add("入库单没有“上次成交价”，已改为进价。");
            priceMode = "cost";
        }
        d.setPriceMode(priceMode);
        String remark = text(args, "remark");
        if (remark != null) {
            d.setRemark(remark);
        }
        String bizDateText = text(args, "bizDate");
        if (bizDateText != null) {
            LocalDate bd = date(args, "bizDate");
            if (bd == null) {
                d.getWarnings().add("日期“" + bizDateText + "”无法识别，未改日期。");
            } else {
                d.setBizDate(fmt(bd));
            }
        }

        // 原单的行
        List<DraftLine> lines = new ArrayList<>();
        Map<Long, DraftLine> bySku = new LinkedHashMap<>();
        for (SourceLine sl : src.getLines()) {
            DraftLine l = new DraftLine();
            l.setId(sl.getId());
            l.setSkuId(sl.getSkuId());
            l.setItem(sl.getItem());
            l.setItemSku(sl.getItemSku());
            l.setItemName(sl.getItemName());
            l.setQuantity(sl.getQuantity());
            l.setSourcePrice(sl.getPrice());
            l.setRemark(sl.getRemark());
            l.setWarehouseId(sl.getWarehouseId());
            lines.add(l);
            if (sl.getSkuId() != null) {
                bySku.put(sl.getSkuId(), l);
            }
        }

        // 逐条应用改动
        List<String> changes = new ArrayList<>();
        JsonNode itemsNode = args.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode it : itemsNode) {
                String name = text(it, "name");
                BigDecimal skuIdNum = decimal(it, "skuId");
                Long skuId = skuIdNum == null ? null : skuIdNum.longValue();
                BigDecimal qty = decimal(it, "quantity");
                BigDecimal price = decimal(it, "price");
                String unit = text(it, "unit");
                String lineRemark = text(it, "remark");
                boolean remove = flag(it, "remove");
                if (name == null && skuId == null) {
                    continue;
                }

                DraftLine target = skuId == null ? null : bySku.get(skuId);
                if (target == null && name != null) {
                    // 先在原单里按名字找（纯记录单的行只有名字）
                    String needle = name.replaceAll("\\s+", "").toLowerCase();
                    for (DraftLine l : lines) {
                        String hay = (lineName(l)).replaceAll("\\s+", "").toLowerCase();
                        if (!needle.isEmpty() && hay.contains(needle)) {
                            target = l;
                            break;
                        }
                    }
                }
                if (target == null && !Boolean.TRUE.equals(src.getRecordOnly())) {
                    SkuPick pick = support.resolveSku(skuId, name, unit);
                    if (pick.found()) {
                        Long sid = pick.picked().getItemSku().getId();
                        target = bySku.get(sid);
                        if (target == null) {
                            if (remove) {
                                d.getWarnings().add("“" + name + "”不在这张单上，无需删除。");
                                continue;
                            }
                            if (pick.warning() != null) {
                                d.getWarnings().add(pick.warning());
                            }
                            target = new DraftLine();
                            target.setSkuId(sid);
                            target.setItem(pick.picked().getItem());
                            target.setItemSku(pick.picked().getItemSku());
                            target.setWarehouseId(src.getWarehouseId());
                            if (qty == null) {
                                d.getWarnings().add("新增的“" + DraftSupport.shortName(pick.picked()) + "”没有给数量，先按 1 填。");
                                qty = BigDecimal.ONE;
                            }
                            lines.add(target);
                            bySku.put(sid, target);
                            changes.add("新增 " + DraftSupport.shortName(pick.picked()));
                        }
                    }
                }
                if (target == null) {
                    if (Boolean.TRUE.equals(src.getRecordOnly()) && !remove && name != null) {
                        // 纯记录单可以直接加一行只有名字的
                        target = new DraftLine();
                        target.setItemName(name);
                        target.setQuantity(qty == null ? BigDecimal.ONE : qty);
                        target.setExplicitPrice(price);
                        target.setRemark(lineRemark);
                        lines.add(target);
                        changes.add("新增 " + name);
                        continue;
                    }
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("name", name != null ? name : ("skuId " + skuId));
                    u.put("reason", "在这张单上没找到对应的行，也没匹配到商品");
                    d.getUnresolved().add(u);
                    continue;
                }
                if (remove) {
                    lines.remove(target);
                    if (target.getSkuId() != null) {
                        bySku.remove(target.getSkuId());
                    }
                    changes.add("删除 " + lineName(target));
                    continue;
                }
                if (qty != null) {
                    changes.add(lineName(target) + " 数量 " + plain(target.getQuantity()) + "→" + plain(qty));
                    target.setQuantity(qty);
                }
                if (price != null) {
                    changes.add(lineName(target) + " 单价 " + target.getSourcePrice() + "→" + price);
                    target.setExplicitPrice(price);
                }
                if (lineRemark != null) {
                    target.setRemark(lineRemark);
                }
            }
        }

        // 单价
        Map<Long, BigDecimal> last = Map.of();
        if ("last".equals(priceMode)) {
            if (d.getMerchantId() == null) {
                d.getWarnings().add("这张单没有客户，无法取“上次成交价”，单价保持不变。");
                priceMode = "keep";
            } else {
                last = support.lastPrices(d.getMerchantId(), bySku.keySet());
            }
        }
        List<String> noPrice = new ArrayList<>();
        for (DraftLine l : lines) {
            BigDecimal p;
            if (l.getExplicitPrice() != null) {
                p = l.getExplicitPrice();
            } else if ("keep".equals(priceMode) || l.getItemSku() == null) {
                p = l.getSourcePrice();
            } else {
                p = support.priceFor(l.getItemSku(), priceMode, null, l.getSourcePrice(), last.get(l.getSkuId()), shipment);
                if (p == null) {
                    noPrice.add(lineName(l));
                    p = l.getSourcePrice();
                }
            }
            l.setPrice(p);
        }
        if (!"keep".equals(priceMode)) {
            changes.add("整单单价改为" + DraftSupport.priceModeLabel(priceMode, shipment));
        }
        if (!noPrice.isEmpty()) {
            d.getWarnings().add("以下商品没有登记" + DraftSupport.priceModeLabel(priceMode, shipment) + "，保持原单价："
                + String.join("、", noPrice.subList(0, Math.min(noPrice.size(), 10))));
        }
        if (changes.isEmpty() && remark == null && bizDateText == null) {
            d.getWarnings().add("没有识别到任何改动，草稿与原单一致。");
        } else {
            d.getWarnings().add(0, "改动：" + String.join("；", changes)
                + (remark != null ? "；备注改为“" + remark + "”" : "")
                + (bizDateText != null ? "；日期改为 " + d.getBizDate() : ""));
        }
        d.setDetails(lines);
        support.finish(d);
        return support.toJson(d);
    }

    @Override
    public String summarizeForModel(String result) {
        return support.summarize(result);
    }

    private static String lineName(DraftLine l) {
        if (l.getItem() != null) {
            return l.getItem().getItemName() + " " + (l.getItemSku() == null ? "" : l.getItemSku().getSkuName());
        }
        return l.getItemName() == null ? String.valueOf(l.getSkuId()) : l.getItemName();
    }

    private String error(String message, String hint) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        m.put("hint", hint);
        return objectMapper.writeValueAsString(m);
    }
}
