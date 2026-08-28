package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.domain.vo.MerchantVo;
import com.ruoyi.wms.domain.vo.WarehouseVo;
import com.ruoyi.wms.service.ItemSkuService;
import com.ruoyi.wms.service.MerchantService;
import com.ruoyi.wms.service.WarehouseService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具：根据自然语言生成「出库单草稿」。
 * <p>
 * 解析客户、仓库、商品+数量，匹配出 merchantId/warehouseId/skuId，组装成草稿对象返回。
 * <b>不写库</b>——草稿由前端填入现有出库编辑页，用户确认后再走正常保存流程。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class CreateShipmentDraftTool implements AiTool {

    private final ItemSkuService itemSkuService;
    private final MerchantService merchantService;
    private final WarehouseService warehouseService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:shipment:all";
    }

    @Override
    public String name() {
        return "create_shipment_draft";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "生成出库单草稿（不会真正出库/保存）。当用户想给某客户出库、发货、卖出、卖给、销售给、发给一批商品，"
            + "或说“帮我记一下/记一笔/登记一下”某笔销售时调用，"
            + "解析客户、商品和数量后返回草稿，前端会预填到出库单表单交由用户确认。"
            + "客户与每个商品的名称尽量按用户原话给出。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("type", "string");
        customer.put("description", "客户名称或编号（可选，没有就留空）");

        Map<String, Object> warehouse = new LinkedHashMap<>();
        warehouse.put("type", "string");
        warehouse.put("description", "出库仓库名称（可选，留空则用默认仓库）");

        Map<String, Object> itemName = new LinkedHashMap<>();
        itemName.put("type", "string");
        itemName.put("description", "商品/规格关键字，如“PU管 10*6.5”");
        Map<String, Object> itemQty = new LinkedHashMap<>();
        itemQty.put("type", "number");
        itemQty.put("description", "出库数量。遇到“一卷/一箱/一包”时填 1，“两卷/2卷”填 2，单位写到 unit 或 remark。");
        Map<String, Object> itemUnit = new LinkedHashMap<>();
        itemUnit.put("type", "string");
        itemUnit.put("description", "数量单位（可选），如卷、箱、包、个。");
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("name", itemName);
        itemProps.put("quantity", itemQty);
        itemProps.put("unit", itemUnit);
        Map<String, Object> itemObj = new LinkedHashMap<>();
        itemObj.put("type", "object");
        itemObj.put("properties", itemProps);
        itemObj.put("required", List.of("name", "quantity"));
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "array");
        items.put("description", "要出库的商品清单");
        items.put("items", itemObj);

        Map<String, Object> remark = new LinkedHashMap<>();
        remark.put("type", "string");
        remark.put("description", "备注（可选）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("customer", customer);
        properties.put("warehouse", warehouse);
        properties.put("items", items);
        properties.put("remark", remark);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("items"));
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> unresolved = new ArrayList<>();
        List<String> unitNotes = new ArrayList<>();

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "shipment");
        String remark = text(args, "remark");

        // 1. 客户
        String customer = text(args, "customer");
        if (customer != null && !customer.isBlank()) {
            List<Long> ids = orderKeywordSearcher.matchMerchantIds(customer);
            if (ids.isEmpty()) {
                warnings.add("未找到客户“" + customer + "”，请在表单中手动选择客户。");
            } else {
                MerchantVo m = merchantService.queryById(ids.get(0));
                if (m != null) {
                    draft.put("merchantId", m.getId());
                    draft.put("merchantName", m.getMerchantName());
                }
                if (ids.size() > 1) {
                    warnings.add("客户“" + customer + "”匹配到多个，已选“"
                        + (m == null ? "" : m.getMerchantName()) + "”，请确认。");
                }
            }
        }

        // 2. 仓库
        Long warehouseId = null;
        String warehouse = text(args, "warehouse");
        if (warehouse != null && !warehouse.isBlank()) {
            List<Long> ids = orderKeywordSearcher.matchWarehouseIds(warehouse);
            if (ids.isEmpty()) {
                warnings.add("未找到仓库“" + warehouse + "”，将使用默认仓库，请确认。");
            } else {
                WarehouseVo w = warehouseService.queryById(ids.get(0));
                if (w != null) {
                    warehouseId = w.getId();
                    draft.put("warehouseId", w.getId());
                    draft.put("warehouseName", w.getWarehouseName());
                }
            }
        }

        // 3. 商品明细
        List<Map<String, Object>> details = new ArrayList<>();
        JsonNode itemsNode = args.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode it : itemsNode) {
                String name = it.path("name").asText("");
                BigDecimal qty = it.has("quantity") ? it.path("quantity").decimalValue() : BigDecimal.ZERO;
                String unit = text(it, "unit");
                if (name.isBlank()) {
                    continue;
                }
                if (unit != null && !unit.isBlank()) {
                    unitNotes.add(name + " " + qty.stripTrailingZeros().toPlainString() + unit);
                }
                PageQuery pq = new PageQuery();
                pq.setPageNum(1);
                pq.setPageSize(5);
                List<ItemSkuMapVo> rows = itemSkuService.queryPageList(new ItemSkuBo(), pq, name).getRows();
                if (rows.isEmpty()) {
                    unresolved.add(Map.of("name", name, "quantity", qty, "reason", "未找到商品"));
                    continue;
                }
                // 按用户所说单位（卷/米/箱…）挑对应计量方式的商品，挑不到才退回第一个
                ItemSkuMapVo picked = AiTool.pickByUnit(rows, unit);
                boolean unitMatched = picked.getItem() != null && AiTool.sameUnitGroup(picked.getItem().getUnit(), unit);
                if (rows.size() > 1) {
                    String pickedName = (picked.getItem() == null ? "" : picked.getItem().getItemName())
                        + " / " + (picked.getItemSku() == null ? "" : picked.getItemSku().getSkuName());
                    if (unitMatched) {
                        warnings.add("“" + name + "”按单位「" + unit + "」选中：" + pickedName + "（单位 "
                            + picked.getItem().getUnit() + "），请确认。");
                    } else {
                        warnings.add("商品“" + name + "”匹配到多个，已选“" + pickedName + "”，请确认是否是想要的计量方式。");
                    }
                }
                BigDecimal price = picked.getItemSku() == null ? null : picked.getItemSku().getSellingPrice();
                BigDecimal amount = price == null ? null : price.multiply(qty);

                Map<String, Object> d = new LinkedHashMap<>();
                d.put("skuId", picked.getItemSku() == null ? null : picked.getItemSku().getId());
                d.put("item", picked.getItem());        // 前端表格显示 itemName
                d.put("itemSku", picked.getItemSku());   // 含 skuName / sellingPrice
                d.put("quantity", qty);
                d.put("amount", amount);
                d.put("warehouseId", warehouseId);       // 可能为 null，前端用默认仓库回填
                details.add(d);
            }
        }
        if (!unitNotes.isEmpty()) {
            String unitRemark = "AI识别数量单位：" + String.join("；", unitNotes);
            remark = remark == null || remark.isBlank() ? unitRemark : remark + "；" + unitRemark;
        }
        draft.put("remark", remark);
        draft.put("details", details);
        draft.put("unresolved", unresolved);
        draft.put("warnings", warnings);
        draft.put("resolvedCount", details.size());

        return objectMapper.writeValueAsString(draft);
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }
}
