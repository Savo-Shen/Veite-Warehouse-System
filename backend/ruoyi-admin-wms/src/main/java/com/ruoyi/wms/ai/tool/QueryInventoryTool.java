package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.InventoryBo;
import com.ruoyi.wms.domain.vo.InventoryVo;
import com.ruoyi.wms.domain.vo.WarehouseVo;
import com.ruoyi.wms.service.InventoryService;
import com.ruoyi.wms.service.WarehouseService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：查询库存。支持按关键字、仓库、库存数量上下限、只看负库存、排序。
 * 用于“某商品还有多少库存 / 在哪个仓库 / 哪些商品库存低于 N / 有没有负库存”。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class QueryInventoryTool implements AiTool {

    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final OrderKeywordSearcher orderKeywordSearcher;
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
    public String title() {
        return "正在查库存";
    }

    @Override
    public String description() {
        return "查询库存：返回每个商品规格在各仓库的库存数量、单位、库位。"
            + "可按商品关键字过滤，也可不给关键字而按库存上下限筛选（如“库存低于 10 的商品”“有哪些负库存”）。"
            + "当用户问某商品还有多少、在哪个仓库、哪些东西快没货了、库存最多/最少的是什么时调用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "keyword", string("商品或规格的名称、编号、条码关键字（可选）。多个词用空格分隔"),
            "warehouse", string("仓库名称（可选），只看该仓库"),
            "maxQuantity", number("只看库存数量 ≤ 该值的记录（可选），如查“库存不足 10 的”传 10"),
            "minQuantity", number("只看库存数量 ≥ 该值的记录（可选）"),
            "negativeOnly", bool("只看负库存（可选）"),
            "sort", enumOf("排序：asc 库存从少到多，desc 从多到少（可选）", "asc", "desc"),
            "limit", integer("最多返回条数，默认 30，最大 100")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String keyword = text(args, "keyword");
        InventoryBo bo = new InventoryBo();
        List<String> notes = new ArrayList<>();

        String warehouse = text(args, "warehouse");
        if (warehouse != null) {
            List<Long> ids = orderKeywordSearcher.matchWarehouseIds(warehouse);
            if (ids.isEmpty()) {
                notes.add("未找到仓库“" + warehouse + "”，已按全部仓库查询");
            } else {
                bo.setWarehouseId(ids.get(0));
            }
        }
        BigDecimal max = decimal(args, "maxQuantity");
        BigDecimal min = decimal(args, "minQuantity");
        bo.setMaxQuantity(max);
        bo.setMinQuantity(min);
        if (flag(args, "negativeOnly")) {
            bo.setNegativeOnly(true);
        }
        String sort = text(args, "sort");
        if ("asc".equalsIgnoreCase(sort)) {
            bo.setSortMode("quantityAsc");
        } else if ("desc".equalsIgnoreCase(sort)) {
            bo.setSortMode("quantityDesc");
        } else if (max != null || flag(args, "negativeOnly")) {
            // 查“低库存”时默认从少到多列，最缺的排前面
            bo.setSortMode("quantityAsc");
        }

        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(1);
        pageQuery.setPageSize(limit(args, "limit", 30, 100));

        TableDataInfo<InventoryVo> page = inventoryService.queryItemBoardList(bo, pageQuery, keyword);

        Map<Long, String> warehouseNameCache = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (InventoryVo row : page.getRows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("商品", row.getItem() == null ? null : row.getItem().getItemName());
            m.put("规格", row.getItemSku() == null ? null : row.getItemSku().getSkuName());
            m.put("单位", row.getItem() == null ? null : row.getItem().getUnit());
            m.put("仓库", warehouseName(warehouseNameCache, row.getWarehouseId()));
            if (row.getLocation() != null) {
                m.put("库位", row.getLocation().getLocationCode());
            }
            m.put("库存数量", plain(row.getQuantity()));
            m.put("skuId", row.getSkuId());
            out.add(m);
            if (row.getQuantity() != null) {
                sum = sum.add(row.getQuantity());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("total", page.getTotal());
        result.put("items", out);
        if (!out.isEmpty()) {
            result.put("列出记录库存合计", plain(sum));
        }
        if (page.getTotal() > out.size()) {
            notes.add("符合条件的共 " + page.getTotal() + " 条，只列出了前 " + out.size() + " 条，可加条件缩小范围或调大 limit");
        }
        if (out.isEmpty() && keyword != null) {
            notes.add("按关键字没查到库存记录。可能是关键字不匹配（试试只用商品名或只用规格），也可能该商品从未入过库");
        }
        if (!notes.isEmpty()) {
            result.put("hint", String.join("；", notes));
        }
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
