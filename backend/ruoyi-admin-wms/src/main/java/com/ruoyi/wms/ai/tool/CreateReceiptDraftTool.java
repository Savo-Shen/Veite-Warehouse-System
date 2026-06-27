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
 * 工具：根据自然语言生成「入库单草稿」。
 * <p>
 * 只返回可预填到现有入库编辑页的结构化草稿，不直接写库。
 */
@Component
@RequiredArgsConstructor
public class CreateReceiptDraftTool implements AiTool {

    private final ItemSkuService itemSkuService;
    private final MerchantService merchantService;
    private final WarehouseService warehouseService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "create_receipt_draft";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "生成入库单草稿（不会真正入库/保存）。当用户想从某供应商入库、采购入库、生产入库、退货入库、补库存、进货、到货或收货时调用，"
            + "解析供应商、仓库、商品和数量后返回草稿，前端会预填到入库单表单交由用户确认。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> supplier = new LinkedHashMap<>();
        supplier.put("type", "string");
        supplier.put("description", "供应商名称或编号（可选，没有就留空）");

        Map<String, Object> warehouse = new LinkedHashMap<>();
        warehouse.put("type", "string");
        warehouse.put("description", "入库仓库名称（可选，留空则用默认仓库）");

        Map<String, Object> optType = new LinkedHashMap<>();
        optType.put("type", "string");
        optType.put("description", "入库类型：1=生产入库，2=采购入库，3=退货入库，4=归还入库。无法判断时用2。");

        Map<String, Object> bizOrderNo = new LinkedHashMap<>();
        bizOrderNo.put("type", "string");
        bizOrderNo.put("description", "业务单号/采购单号（可选）");

        Map<String, Object> itemName = new LinkedHashMap<>();
        itemName.put("type", "string");
        itemName.put("description", "商品/规格关键字，如“PU管 10*6.5”");
        Map<String, Object> itemQty = new LinkedHashMap<>();
        itemQty.put("type", "number");
        itemQty.put("description", "入库数量。遇到“一卷/一捆/一箱”时填 1，“两卷/2卷”填 2，单位写到 unit。");
        Map<String, Object> itemUnit = new LinkedHashMap<>();
        itemUnit.put("type", "string");
        itemUnit.put("description", "数量单位（可选），如卷、捆、米、箱、包、个。同一商品可能有“按米”和“按卷/捆”两种，单位决定选哪种。");
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
        items.put("description", "要入库的商品清单");
        items.put("items", itemObj);

        Map<String, Object> remark = new LinkedHashMap<>();
        remark.put("type", "string");
        remark.put("description", "备注（可选）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("supplier", supplier);
        properties.put("warehouse", warehouse);
        properties.put("optType", optType);
        properties.put("bizOrderNo", bizOrderNo);
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

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "receipt");
        draft.put("optType", normalizeOptType(text(args, "optType")));
        draft.put("bizOrderNo", text(args, "bizOrderNo"));
        draft.put("remark", text(args, "remark"));

        String supplier = text(args, "supplier");
        if (supplier != null && !supplier.isBlank()) {
            List<Long> ids = orderKeywordSearcher.matchMerchantIds(supplier);
            if (ids.isEmpty()) {
                warnings.add("未找到供应商“" + supplier + "”，请在表单中手动选择供应商。");
            } else {
                MerchantVo m = merchantService.queryById(ids.get(0));
                if (m != null) {
                    draft.put("merchantId", m.getId());
                    draft.put("merchantName", m.getMerchantName());
                }
                if (ids.size() > 1) {
                    warnings.add("供应商“" + supplier + "”匹配到多个，已选“"
                        + (m == null ? "" : m.getMerchantName()) + "”，请确认。");
                }
            }
        }

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

                BigDecimal price = picked.getItemSku() == null ? null : picked.getItemSku().getCostPrice();
                BigDecimal amount = price == null ? null : price.multiply(qty);

                Map<String, Object> d = new LinkedHashMap<>();
                d.put("skuId", picked.getItemSku() == null ? null : picked.getItemSku().getId());
                d.put("item", picked.getItem());
                d.put("itemSku", picked.getItemSku());
                d.put("quantity", qty);
                d.put("amount", amount);
                d.put("warehouseId", warehouseId);
                details.add(d);
            }
        }
        draft.put("details", details);
        draft.put("unresolved", unresolved);
        draft.put("warnings", warnings);
        draft.put("resolvedCount", details.size());

        return objectMapper.writeValueAsString(draft);
    }

    private String normalizeOptType(String value) {
        if (value == null || value.isBlank()) {
            return "2";
        }
        return switch (value) {
            case "1", "生产入库" -> "1";
            case "3", "退货入库" -> "3";
            case "4", "归还入库" -> "4";
            default -> "2";
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText(null);
    }
}
