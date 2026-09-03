package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.ai.tool.DraftSupport.SkuPick;
import com.ruoyi.wms.domain.bo.WarehouseBo;
import com.ruoyi.wms.domain.entity.Inventory;
import com.ruoyi.wms.domain.vo.WarehouseVo;
import com.ruoyi.wms.mapper.InventoryMapper;
import com.ruoyi.wms.service.WarehouseService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：生成「移库单草稿」——仓库之间调拨。不落库，前端预填到移库单编辑页。
 * 货架库位之间的挪动不是移库单，由 {@link RelocateSkuDraftTool} 处理。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class CreateMovementDraftTool implements AiTool {

    private final DraftSupport support;
    private final WarehouseService warehouseService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final InventoryMapper inventoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:movement:all";
    }

    @Override
    public String name() {
        return "create_movement_draft";
    }

    @Override
    public String title() {
        return "正在生成移库草稿";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "生成移库单草稿：把商品从一个仓库调拨到另一个仓库（不会直接保存，前端预填到移库单表单交由用户确认）。"
            + "只用于仓库之间的调拨；把商品换到另一个货架库位（如 1-A1-3 → 2-B2-2）不是移库单，请改用 relocate_sku_draft。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> item = object(props(
            "name", string("商品/规格关键字（已知 skuId 时可省略）"),
            "skuId", number("商品规格 id（可选）"),
            "quantity", number("移库数量"),
            "unit", string("数量单位（可选）")
        ), List.of("quantity"));
        return object(props(
            "sourceWarehouse", string("调出仓库名称（可选，系统只有一个仓库时自动填）"),
            "targetWarehouse", string("调入仓库名称"),
            "items", array("要移的商品清单", item),
            "remark", string("备注（可选）")
        ), List.of("targetWarehouse", "items"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> unresolved = new ArrayList<>();
        List<WarehouseVo> all = warehouseService.queryList(new WarehouseBo());

        WarehouseVo source = pick(text(args, "sourceWarehouse"));
        WarehouseVo target = pick(text(args, "targetWarehouse"));
        if (source == null && all.size() == 1) {
            source = all.get(0);
        }
        if (target == null || source == null) {
            String names = all.stream().map(WarehouseVo::getWarehouseName).reduce((a, b) -> a + "、" + b).orElse("（无）");
            if (all.size() <= 1) {
                return error("系统目前只有一个仓库（" + names + "），没有可调入的目标仓库；移库单是仓库之间的调拨。"
                    + "如果是想把商品换到另一个货架库位（如 2-B2-2），请用 relocate_sku_draft。");
            }
            return error((target == null ? "没找到调入仓库" : "没找到调出仓库") + "。现有仓库：" + names);
        }
        if (source.getId().equals(target.getId())) {
            return error("调出和调入是同一个仓库（" + source.getWarehouseName() + "），不需要移库。"
                + "如果是想换货架库位，请用 relocate_sku_draft。");
        }

        List<Map<String, Object>> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        JsonNode items = args.path("items");
        if (items.isArray()) {
            for (JsonNode it : items) {
                String name = text(it, "name");
                BigDecimal skuIdNum = decimal(it, "skuId");
                Long skuId = skuIdNum == null ? null : skuIdNum.longValue();
                BigDecimal qty = decimal(it, "quantity");
                if (name == null && skuId == null) {
                    continue;
                }
                SkuPick pick = support.resolveSku(skuId, name, text(it, "unit"));
                if (!pick.found()) {
                    unresolved.add(Map.of("name", name != null ? name : ("skuId " + skuId), "reason", pick.reason()));
                    continue;
                }
                if (pick.warning() != null) {
                    warnings.add(pick.warning());
                }
                Long sid = pick.picked().getItemSku().getId();
                Inventory inv = inventoryMapper.selectOne(Wrappers.<Inventory>lambdaQuery()
                    .eq(Inventory::getSkuId, sid).eq(Inventory::getWarehouseId, source.getId()).last("limit 1"));
                BigDecimal available = inv == null ? BigDecimal.ZERO : inv.getQuantity();
                String shortName = DraftSupport.shortName(pick.picked());
                if (qty == null || qty.signum() <= 0) {
                    warnings.add("“" + shortName + "”没有给数量，先按 1 填。");
                    qty = BigDecimal.ONE;
                }
                if (available == null || available.signum() <= 0) {
                    warnings.add("“" + shortName + "”在 " + source.getWarehouseName() + " 没有库存，移不出去。");
                } else if (available.compareTo(qty) < 0) {
                    warnings.add("“" + shortName + "”在 " + source.getWarehouseName() + " 只有 " + plain(available) + "，不够移 " + plain(qty) + "。");
                }
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("skuId", sid);
                d.put("item", pick.picked().getItem());
                d.put("itemSku", pick.picked().getItemSku());
                d.put("quantity", qty);
                d.put("available", plain(available));
                d.put("sourceWarehouseId", source.getId());
                d.put("targetWarehouseId", target.getId());
                details.add(d);
                total = total.add(qty);
            }
        }
        if (details.isEmpty() && unresolved.isEmpty()) {
            return error("items 为空，没有可移库的商品");
        }

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "movement");
        draft.put("mode", "new");
        draft.put("sourceWarehouseId", source.getId());
        draft.put("sourceWarehouseName", source.getWarehouseName());
        draft.put("targetWarehouseId", target.getId());
        draft.put("targetWarehouseName", target.getWarehouseName());
        draft.put("remark", text(args, "remark"));
        draft.put("details", details);
        draft.put("unresolved", unresolved);
        draft.put("warnings", warnings);
        draft.put("resolvedCount", details.size());
        draft.put("totalQuantity", plain(total));
        return objectMapper.writeValueAsString(draft);
    }

    private WarehouseVo pick(String keyword) {
        if (keyword == null) {
            return null;
        }
        List<Long> ids = orderKeywordSearcher.matchWarehouseIds(keyword);
        return ids.isEmpty() ? null : warehouseService.queryById(ids.get(0));
    }

    @Override
    public String summarizeForModel(String result) {
        return support.summarizeGeneric(result);
    }

    private String error(String msg) throws Exception {
        return objectMapper.writeValueAsString(Map.of("error", msg));
    }
}
