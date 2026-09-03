package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.InventoryHistoryBo;
import com.ruoyi.wms.domain.vo.InventoryHistoryVo;
import com.ruoyi.wms.service.InventoryHistoryService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：库存流水——每一次库存变动（入库/出库/移库/盘点）的前后数量，用来追溯“库存怎么变成这个数的”。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class QueryInventoryHistoryTool implements AiTool {

    private final InventoryHistoryService inventoryHistoryService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final DraftSupport support;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:inventoryHistory:all";
    }

    @Override
    public String name() {
        return "query_inventory_history";
    }

    @Override
    public String title() {
        return "正在查库存流水";
    }

    @Override
    public String description() {
        return "查库存流水：某商品每一次库存变动（入库、出库、移库、盘点）的时间、单号、变动量、变动前/后数量。"
            + "当用户问“X 的库存怎么从 41 变成 5 的”“上次盘点把 X 改成了多少”“最近谁动过 X 的库存”时调用。"
            + "只看单据明细用 query_order_history；这个工具的特点是含盘点和移库、并且有变动前后的数量。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "keyword", string("商品/规格关键字（可选）"),
            "orderType", enumOf("变动类型（可选）：receipt 入库，shipment 出库，movement 移库，check 盘点",
                "receipt", "shipment", "movement", "check"),
            "orderNo", string("单号（可选）"),
            "warehouse", string("仓库名称（可选）"),
            "beginDate", string("起始日期 yyyy-MM-dd（可选）"),
            "endDate", string("截止日期 yyyy-MM-dd（可选）"),
            "limit", integer("最多返回条数，默认 30，最大 100")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        InventoryHistoryBo bo = new InventoryHistoryBo();
        bo.setKeyword(text(args, "keyword"));
        bo.setOrderNo(text(args, "orderNo"));
        String type = text(args, "orderType");
        bo.setOrderType(switch (type == null ? "" : type) {
            case "receipt" -> 1;
            case "shipment" -> 2;
            case "movement" -> 3;
            case "check" -> 4;
            default -> null;
        });
        List<String> notes = new ArrayList<>();
        String wh = text(args, "warehouse");
        if (wh != null) {
            List<Long> ids = orderKeywordSearcher.matchWarehouseIds(wh);
            if (ids.isEmpty()) {
                notes.add("未找到仓库“" + wh + "”，已按全部仓库查询");
            } else {
                bo.setWarehouseId(ids.get(0));
            }
        }
        LocalDate begin = date(args, "beginDate");
        LocalDate end = date(args, "endDate");
        if (begin != null || end != null) {
            bo.setStartTime(fmt(begin == null ? LocalDate.of(2000, 1, 1) : begin));
            bo.setEndTime(fmt(end == null ? LocalDate.now().plusYears(1) : end));
        }
        if (bo.getKeyword() == null && bo.getOrderNo() == null && bo.getOrderType() == null && begin == null && end == null) {
            return "{\"error\":\"商品关键字、单号、变动类型、日期范围至少给一个\"}";
        }
        PageQuery pq = new PageQuery();
        pq.setPageNum(1);
        pq.setPageSize(limit(args, "limit", 30, 100));
        TableDataInfo<InventoryHistoryVo> page = inventoryHistoryService.queryPageList(bo, pq);

        Map<Long, String> whCache = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (InventoryHistoryVo h : page.getRows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("时间", h.getBizDate() != null ? fmt(h.getBizDate()) : fmt(h.getCreateTime()));
            m.put("单号", h.getOrderNo());
            m.put("类型", typeText(h.getOrderType()));
            m.put("商品", h.getItem() == null ? null : h.getItem().getItemName());
            m.put("规格", h.getItemSku() == null ? null : h.getItemSku().getSkuName());
            m.put("单位", h.getItem() == null ? null : h.getItem().getUnit());
            m.put("变动", h.getQuantity() == null ? null : (h.getQuantity().signum() > 0 ? "+" : "") + plain(h.getQuantity()));
            m.put("变动前", plain(h.getBeforeQuantity()));
            m.put("变动后", plain(h.getAfterQuantity()));
            m.put("金额", h.getAmount());
            m.put("仓库", h.getWarehouseId() == null ? null : whCache.computeIfAbsent(h.getWarehouseId(), support::warehouseName));
            m.put("备注", h.getRemark());
            m.put("skuId", h.getSkuId());
            out.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("total", page.getTotal());
        result.put("note", "按时间倒序；变动量正数是增加、负数是减少，盘点行的变动 = 实盘 - 账面");
        result.put("items", out);
        if (page.getTotal() > out.size()) {
            notes.add("共 " + page.getTotal() + " 条，只列出最近 " + out.size() + " 条，可加日期范围或调大 limit");
        }
        if (out.isEmpty()) {
            notes.add("没有符合条件的流水。可能关键字不匹配，或该商品从未有过库存变动");
        }
        if (!notes.isEmpty()) {
            result.put("hint", String.join("；", notes));
        }
        return objectMapper.writeValueAsString(result);
    }

    private static String typeText(Integer t) {
        if (t == null) {
            return "";
        }
        return switch (t) {
            case 1 -> "入库";
            case 2 -> "出库";
            case 3 -> "移库";
            case 4 -> "盘点";
            default -> String.valueOf(t);
        };
    }
}
