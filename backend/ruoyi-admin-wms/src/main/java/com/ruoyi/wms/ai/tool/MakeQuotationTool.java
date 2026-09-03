package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.ai.tool.DraftSupport.Draft;
import com.ruoyi.wms.ai.tool.DraftSupport.SkuPick;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：客户报价单——按售价/上次成交价/进价加成算一份报价，带毛利，并生成能直接发给客户的文字。只读。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class MakeQuotationTool implements AiTool {

    private final DraftSupport support;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:item:list";
    }

    @Override
    public List<String> permissionsUsed() {
        return List.of("wms:item:list", DraftSupport.PERM_SHIPMENT);
    }

    @Override
    public String name() {
        return "make_quotation";
    }

    @Override
    public String title() {
        return "正在整理报价单";
    }

    @Override
    public String description() {
        return "生成客户报价单（只算价，不建单）：按售价、该客户上次成交价、或进价×加价倍数给每个商品报单价和金额，"
            + "附成本和毛利（给员工看），并生成一段可直接复制发给客户的报价文字。"
            + "当用户说“给约克报个价：气缸 10 个、气管 3 卷”“帮我算一下这几样多少钱”时调用。"
            + "如果之后用户要开单，再用 create_shipment_draft 传同样的 items（可直接用返回的 skuId）。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> item = object(props(
            "name", string("商品/规格关键字（已知 skuId 时可省略）"),
            "skuId", number("商品规格 id（可选）"),
            "quantity", number("数量"),
            "unit", string("数量单位（可选）"),
            "price", number("指定单价（可选，用户明确说了才填）")
        ), List.of("quantity"));
        return object(props(
            "customer", string("客户名称（可选，用上次成交价时必须）"),
            "items", array("要报价的商品", item),
            "priceMode", enumOf("单价取法：selling 售价（默认）；last 该客户上次成交价（没有则售价）；markup 进价×加价倍数",
                "selling", "last", "markup"),
            "markup", number("加价倍数（priceMode=markup 时用，如 1.3 表示进价加三成）"),
            "remark", string("备注（可选），如交期、含税与否")
        ), List.of("items"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        Draft tmp = new Draft();
        support.resolveMerchant(tmp, text(args, "customer"), 1, "客户");
        List<String> warnings = new ArrayList<>(tmp.getWarnings());
        List<Map<String, Object>> unresolved = new ArrayList<>();
        String priceMode = text(args, "priceMode");
        if (priceMode == null) {
            priceMode = "selling";
        }
        BigDecimal markup = decimal(args, "markup");
        if ("markup".equals(priceMode) && (markup == null || markup.signum() <= 0)) {
            warnings.add("没有给加价倍数，按 1.3 算。");
            markup = new BigDecimal("1.3");
        }
        if ("last".equals(priceMode) && tmp.getMerchantId() == null) {
            warnings.add("要用上次成交价需要先确定客户，这次按售价。");
            priceMode = "selling";
        }

        record Line(ItemSkuMapVo sku, BigDecimal qty, String unit, BigDecimal explicit) {
        }
        List<Line> lines = new ArrayList<>();
        JsonNode items = args.path("items");
        if (items.isArray()) {
            for (JsonNode it : items) {
                String name = text(it, "name");
                BigDecimal skuIdNum = decimal(it, "skuId");
                Long skuId = skuIdNum == null ? null : skuIdNum.longValue();
                if (name == null && skuId == null) {
                    continue;
                }
                String unit = text(it, "unit");
                SkuPick pick = support.resolveSku(skuId, name, unit);
                if (!pick.found()) {
                    unresolved.add(Map.of("name", name != null ? name : ("skuId " + skuId), "reason", pick.reason()));
                    continue;
                }
                if (pick.warning() != null) {
                    warnings.add(pick.warning());
                }
                BigDecimal qty = decimal(it, "quantity");
                if (qty == null || qty.signum() <= 0) {
                    qty = BigDecimal.ONE;
                    warnings.add("“" + DraftSupport.shortName(pick.picked()) + "”没有给数量，按 1 算。");
                }
                lines.add(new Line(pick.picked(), qty, unit, decimal(it, "price")));
            }
        }
        if (lines.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of("error", "没有可报价的商品", "unresolved", unresolved));
        }

        Map<Long, BigDecimal> last = "last".equals(priceMode)
            ? support.lastPrices(tmp.getMerchantId(), lines.stream().map(l -> l.sku().getItemSku().getId()).toList())
            : Map.of();

        List<Map<String, Object>> out = new ArrayList<>();
        StringBuilder txt = new StringBuilder();
        txt.append("报价单").append("\n");
        if (tmp.getMerchantName() != null) {
            txt.append("客户：").append(tmp.getMerchantName()).append("\n");
        }
        txt.append("日期：").append(fmt(LocalDate.now())).append("\n");
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean costMissing = false;
        int i = 1;
        for (Line l : lines) {
            var sku = l.sku().getItemSku();
            BigDecimal cost = sku.getCostPrice();
            BigDecimal price;
            String basis;
            if (l.explicit() != null) {
                price = l.explicit();
                basis = "指定";
            } else if ("last".equals(priceMode) && last.get(sku.getId()) != null) {
                price = last.get(sku.getId());
                basis = "上次成交价";
            } else if ("markup".equals(priceMode) && cost != null) {
                price = cost.multiply(markup).setScale(2, RoundingMode.HALF_UP);
                basis = "进价×" + plain(markup);
            } else {
                price = sku.getSellingPrice();
                basis = "售价";
            }
            if (price == null) {
                warnings.add("“" + DraftSupport.shortName(l.sku()) + "”没有登记售价，单价留空。");
            }
            BigDecimal amount = price == null ? null : price.multiply(l.qty()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costAmt = cost == null ? null : cost.multiply(l.qty()).setScale(2, RoundingMode.HALF_UP);
            if (amount != null) {
                totalAmount = totalAmount.add(amount);
            }
            if (costAmt != null) {
                totalCost = totalCost.add(costAmt);
            } else {
                costMissing = true;
            }
            String unit = l.unit() != null ? l.unit() : (l.sku().getItem() == null || l.sku().getItem().getUnit() == null ? "" : l.sku().getItem().getUnit());
            String itemName = l.sku().getItem() == null ? "" : l.sku().getItem().getItemName();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", sku.getId());
            m.put("商品", itemName);
            m.put("规格", sku.getSkuName());
            m.put("数量", plain(l.qty()));
            m.put("单位", unit);
            m.put("单价", price);
            m.put("单价依据", basis);
            m.put("金额", amount);
            m.put("进价", cost);
            m.put("毛利", amount == null || costAmt == null ? null : amount.subtract(costAmt));
            out.add(m);

            txt.append(i++).append(". ").append(itemName).append(" ").append(sku.getSkuName())
                .append(" × ").append(plain(l.qty()).toPlainString()).append(unit)
                .append("  @").append(price == null ? "？" : money(price).toPlainString())
                .append(" = ").append(amount == null ? "？" : amount.toPlainString()).append(" 元\n");
        }
        txt.append("合计：").append(money(totalAmount).toPlainString()).append(" 元");
        String remark = text(args, "remark");
        if (remark != null) {
            txt.append("\n备注：").append(remark);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("客户", tmp.getMerchantName());
        result.put("单价取法", priceMode);
        result.put("lines", out);
        result.put("合计金额", money(totalAmount));
        result.put("合计成本", costMissing ? null : money(totalCost));
        result.put("合计毛利", costMissing ? null : money(totalAmount.subtract(totalCost)));
        if (!costMissing && totalAmount.signum() > 0) {
            result.put("毛利率", totalAmount.subtract(totalCost).multiply(BigDecimal.valueOf(100))
                .divide(totalAmount, 1, RoundingMode.HALF_UP) + "%");
        }
        result.put("报价文字", txt.toString());
        if (!unresolved.isEmpty()) {
            result.put("unresolved", unresolved);
        }
        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }
        result.put("note", "进价、毛利只给员工看，发给客户的文字里不要带；用户要开单时用 create_shipment_draft 传同样的 skuId 和数量");
        return objectMapper.writeValueAsString(result);
    }
}
