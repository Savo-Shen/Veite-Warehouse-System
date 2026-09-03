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
 * 工具：生成「盘点单草稿」——把实盘数量和账面数量对上，差异由用户在盘点单表单里确认后落库。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class CreateCheckDraftTool implements AiTool {

    private final DraftSupport support;
    private final WarehouseService warehouseService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final InventoryMapper inventoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:check:all";
    }

    @Override
    public String name() {
        return "create_check_draft";
    }

    @Override
    public String title() {
        return "正在生成盘点草稿";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "生成盘点单草稿（不会直接保存，前端预填到盘点单表单交由用户确认后才调整库存）。"
            + "当用户报实盘数量，如“盘点：SDA32*20 实际 40 个，PC08-02 有 500 个”“气管 10*6.5 数了一下是 3 卷”，"
            + "或要把账面库存改成某个数、补正负库存时调用。每个商品给实盘数量 checkQuantity，工具会自动带出账面数量算差异。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> item = object(props(
            "name", string("商品/规格关键字（已知 skuId 时可省略）"),
            "skuId", number("商品规格 id（可选）"),
            "checkQuantity", number("实盘数量（现场数出来的数）"),
            "unit", string("数量单位（可选），用于区分按米/按卷的商品记录")
        ), List.of("checkQuantity"));
        return object(props(
            "warehouse", string("仓库名称（可选，系统只有一个仓库时自动填）"),
            "items", array("盘点的商品清单", item),
            "remark", string("备注（可选）")
        ), List.of("items"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> unresolved = new ArrayList<>();

        WarehouseVo warehouse = null;
        String wh = text(args, "warehouse");
        if (wh != null) {
            List<Long> ids = orderKeywordSearcher.matchWarehouseIds(wh);
            if (ids.isEmpty()) {
                warnings.add("未找到仓库“" + wh + "”，已按默认仓库。");
            } else {
                warehouse = warehouseService.queryById(ids.get(0));
            }
        }
        if (warehouse == null) {
            List<WarehouseVo> all = warehouseService.queryList(new WarehouseBo());
            if (all.isEmpty()) {
                return error("系统里还没有仓库");
            }
            warehouse = all.get(0);
            if (all.size() > 1 && wh == null) {
                warnings.add("没有指定仓库，已按“" + warehouse.getWarehouseName() + "”，请确认。");
            }
        }

        List<Map<String, Object>> details = new ArrayList<>();
        BigDecimal totalDiff = BigDecimal.ZERO;
        JsonNode items = args.path("items");
        if (items.isArray()) {
            for (JsonNode it : items) {
                String name = text(it, "name");
                BigDecimal skuIdNum = decimal(it, "skuId");
                Long skuId = skuIdNum == null ? null : skuIdNum.longValue();
                BigDecimal checkQty = decimal(it, "checkQuantity");
                if (name == null && skuId == null) {
                    continue;
                }
                if (checkQty == null) {
                    unresolved.add(Map.of("name", name != null ? name : ("skuId " + skuId), "reason", "没有给实盘数量"));
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
                    .eq(Inventory::getSkuId, sid).eq(Inventory::getWarehouseId, warehouse.getId()).last("limit 1"));
                BigDecimal book = inv == null || inv.getQuantity() == null ? BigDecimal.ZERO : inv.getQuantity();
                BigDecimal diff = checkQty.subtract(book);
                if (inv == null) {
                    warnings.add("“" + DraftSupport.shortName(pick.picked()) + "”在 " + warehouse.getWarehouseName()
                        + " 还没有库存记录，保存盘点后会按实盘数新建。");
                }
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("skuId", sid);
                d.put("item", pick.picked().getItem());
                d.put("itemSku", pick.picked().getItemSku());
                d.put("inventoryId", inv == null ? null : inv.getId());
                d.put("warehouseId", warehouse.getId());
                d.put("quantity", plain(book));
                d.put("checkQuantity", plain(checkQty));
                d.put("diff", plain(diff));
                d.put("newInventory", inv == null);
                details.add(d);
                totalDiff = totalDiff.add(diff);
            }
        }
        if (details.isEmpty() && unresolved.isEmpty()) {
            return error("items 为空，没有可盘点的商品");
        }

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "check");
        draft.put("mode", "new");
        draft.put("warehouseId", warehouse.getId());
        draft.put("warehouseName", warehouse.getWarehouseName());
        draft.put("remark", text(args, "remark"));
        draft.put("details", details);
        draft.put("unresolved", unresolved);
        draft.put("warnings", warnings);
        draft.put("resolvedCount", details.size());
        draft.put("totalDiff", plain(totalDiff));
        draft.put("note", "diff = 实盘 - 账面，正数是盘盈、负数是盘亏；保存盘点单后库存会改成实盘数");
        return objectMapper.writeValueAsString(draft);
    }

    @Override
    public String summarizeForModel(String result) {
        return support.summarizeGeneric(result);
    }

    private String error(String msg) throws Exception {
        return objectMapper.writeValueAsString(Map.of("error", msg));
    }
}
