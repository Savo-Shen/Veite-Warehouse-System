package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.wms.domain.bo.InventoryBo;
import com.ruoyi.wms.domain.vo.InventoryVo;
import com.ruoyi.wms.domain.vo.WarehouseVo;
import com.ruoyi.wms.service.InventoryService;
import com.ruoyi.wms.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具：按关键字查询商品库存（各仓库数量）。
 * 用于“某商品还有多少库存 / 在哪个仓库”。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class QueryInventoryTool implements AiTool {

    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:inventory:all";
    }

    @Override
    public String name() {
        return "query_inventory";
    }

    @Override
    public String description() {
        return "根据关键字查询商品库存，返回每个商品规格在各仓库的库存数量。"
            + "当用户问某商品还有多少库存、库存在哪个仓库时调用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "商品或规格的名称、编号、条码关键字");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", keyword);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("keyword"));
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String keyword = args.path("keyword").asText("");

        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(1);
        pageQuery.setPageSize(30);

        List<InventoryVo> rows = inventoryService.queryItemBoardList(new InventoryBo(), pageQuery, keyword).getRows();

        Map<Long, String> warehouseNameCache = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (InventoryVo row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("商品", row.getItem() == null ? null : row.getItem().getItemName());
            m.put("规格", row.getItemSku() == null ? null : row.getItemSku().getSkuName());
            m.put("仓库", warehouseName(warehouseNameCache, row.getWarehouseId()));
            m.put("库存数量", row.getQuantity());
            out.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("items", out);
        return objectMapper.writeValueAsString(result);
    }

    private String warehouseName(Map<Long, String> cache, Long warehouseId) {
        if (warehouseId == null) {
            return null;
        }
        return cache.computeIfAbsent(warehouseId, id -> {
            WarehouseVo vo = warehouseService.queryById(id);
            return vo == null ? null : vo.getWarehouseName();
        });
    }
}
