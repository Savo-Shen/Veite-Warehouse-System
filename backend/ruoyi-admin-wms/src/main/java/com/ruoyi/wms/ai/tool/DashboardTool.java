package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：经营概览（看板数据）——今日出入库、待处理单据、库存总量与价值、最近趋势、热销与低库存。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class DashboardTool implements AiTool {

    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:inventory:all";
    }

    @Override
    public String name() {
        return "get_dashboard";
    }

    @Override
    public String title() {
        return "正在拉取经营概览";
    }

    @Override
    public String description() {
        return "经营概览（看板）：今天出库/入库的单数、数量、金额，待出库/待入库单数，库存总量和按售价/进价的库存价值，"
            + "低库存和零库存规格数，最近 7 天出入库趋势，近 30 天出库最多的商品，库存最少的商品。"
            + "当用户问“今天怎么样”“今天出了几单”“库存值多少钱”“最近卖得最好的是什么”时调用，不需要参数。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "trendDays", integer("趋势要最近几天，默认 7，最大 30")
        ), List.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(JsonNode args) throws Exception {
        int days = limit(args, "trendDays", 7, 30);
        Map<String, Object> ov = dashboardService.overview();
        Map<String, Object> s = (Map<String, Object>) ov.getOrDefault("summary", Map.of());

        Map<String, Object> today = new LinkedHashMap<>();
        today.put("出库单数", s.get("todayShipmentOrders"));
        today.put("出库数量", s.get("todayShipmentQuantity"));
        today.put("出库金额", s.get("todayTurnover"));
        today.put("入库单数", s.get("todayReceiptOrders"));
        today.put("入库数量", s.get("todayReceiptQuantity"));
        today.put("入库金额", s.get("todayReceiptAmount"));

        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("待出库单数", s.get("pendingShipmentOrders"));
        pending.put("待入库单数", s.get("pendingReceiptOrders"));

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("商品数", s.get("itemCount"));
        stock.put("规格数", s.get("skuCount"));
        stock.put("库存总数量", s.get("totalStockQuantity"));
        stock.put("库存售价价值", s.get("stockSellingValue"));
        stock.put("库存成本价值", s.get("stockCostValue"));
        stock.put("低库存规格数(1~5)", s.get("lowStockSkuCount"));
        stock.put("零库存规格数", s.get("emptyStockSkuCount"));
        stock.put("仓库数", s.get("warehouseCount"));
        stock.put("库位数", s.get("locationCount"));

        List<Map<String, Object>> trend = new ArrayList<>();
        List<Map<String, Object>> daily = (List<Map<String, Object>>) ov.getOrDefault("dailyTrend", List.of());
        int from = Math.max(0, daily.size() - days);
        for (Map<String, Object> d : daily.subList(from, daily.size())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("日期", String.valueOf(d.get("day")));
            m.put("出库单数", d.get("shipmentOrders"));
            m.put("出库金额", d.get("turnover"));
            m.put("入库单数", d.get("receiptOrders"));
            m.put("入库金额", d.get("receiptAmount"));
            trend.add(m);
        }

        List<Map<String, Object>> top = new ArrayList<>();
        for (Map<String, Object> t : (List<Map<String, Object>>) ov.getOrDefault("topShipmentSku", List.of())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("商品", t.get("itemName"));
            m.put("规格", t.get("skuName"));
            m.put("数量", t.get("quantity"));
            m.put("金额", t.get("amount"));
            top.add(m);
        }
        List<Map<String, Object>> low = new ArrayList<>();
        for (Map<String, Object> t : (List<Map<String, Object>>) ov.getOrDefault("lowStockSku", List.of())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("商品", t.get("itemName"));
            m.put("规格", t.get("skuName"));
            m.put("库存", t.get("quantity"));
            m.put("库位", t.get("locationCode"));
            low.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("今日", today);
        result.put("待处理", pending);
        result.put("库存", stock);
        result.put("最近趋势(按天)", trend);
        result.put("近30天出库最多", top);
        result.put("库存最少(1~5)", low);
        result.put("note", "今日按业务日期统计、只算已出库/已入库；库存价值 = 库存数量 × 当前售价/进价");
        return objectMapper.writeValueAsString(result);
    }
}
