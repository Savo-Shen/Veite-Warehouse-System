package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.wms.ai.mapper.AiStatsMapper;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.service.ItemSkuService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
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
 * 工具：出入库统计聚合——按客户/商品/规格/分类/月/日汇总数量、金额、成本、毛利。
 * 让数据库做加法，别让模型拉一百行自己算。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class QueryStatsTool implements AiTool {

    private final AiStatsMapper aiStatsMapper;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final ItemSkuService itemSkuService;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> permissionsUsed() {
        return List.of(DraftSupport.PERM_SHIPMENT, DraftSupport.PERM_RECEIPT);
    }

    @Override
    public String name() {
        return "query_stats";
    }

    @Override
    public String title() {
        return "正在统计";
    }

    @Override
    public String description() {
        return "出入库统计汇总（数据库直接算好）：按客户/供应商、商品、规格、分类、月份、日期分组，给出单据数、数量、金额，"
            + "出库还带成本和毛利。当用户问“这个月出了多少钱”“上季度哪个客户买最多”“气缸这半年卖了多少”"
            + "“每个月的销售额”“约克今年一共买了多少”时用这个，不要用 query_order_history 拉明细自己加。"
            + "只统计已出库/已入库的单据。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "orderType", enumOf("shipment 出库/销售（默认），receipt 入库/采购", "shipment", "receipt"),
            "groupBy", enumOf("分组：merchant 按客户/供应商（默认），item 按商品，sku 按规格，category 按分类，month 按月，day 按日，none 只要总计",
                "merchant", "item", "sku", "category", "month", "day", "none"),
            "beginDate", string("起始日期 yyyy-MM-dd（可选）"),
            "endDate", string("截止日期 yyyy-MM-dd（可选，含当天）"),
            "merchant", string("只统计某客户/供应商（可选）"),
            "keyword", string("只统计某商品/规格（可选）"),
            "sort", enumOf("排序：amount 按金额（默认），quantity 按数量，time 按时间（按月/日分组时用）", "amount", "quantity", "time"),
            "limit", integer("最多返回分组数，默认 20，最大 100")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        boolean shipment = !"receipt".equals(text(args, "orderType"));
        if (!AiToolContext.has(shipment ? DraftSupport.PERM_SHIPMENT : DraftSupport.PERM_RECEIPT)) {
            return "{\"error\":\"当前账号没有查看" + (shipment ? "出库" : "入库") + "数据的权限\"}";
        }
        String groupBy = text(args, "groupBy");
        if (groupBy == null) {
            groupBy = "merchant";
        }
        String sort = text(args, "sort");
        if (sort == null) {
            sort = ("month".equals(groupBy) || "day".equals(groupBy)) ? "time" : "amount";
        }
        LocalDate begin = date(args, "beginDate");
        LocalDate end = date(args, "endDate");
        if (begin == null && end != null) {
            begin = LocalDate.of(2000, 1, 1);
        }
        if (begin != null && end == null) {
            end = LocalDate.now().plusYears(1);
        }
        List<String> notes = new ArrayList<>();

        List<Long> merchantIds = null;
        String merchant = text(args, "merchant");
        if (merchant != null) {
            merchantIds = orderKeywordSearcher.matchMerchantIds(merchant);
            if (merchantIds.isEmpty()) {
                return objectMapper.writeValueAsString(Map.of("count", 0, "items", List.of(),
                    "hint", "没有找到叫“" + merchant + "”的往来单位，可用 search_merchant 确认全称"));
            }
        }
        List<Long> skuIds = null;
        String keyword = text(args, "keyword");
        if (keyword != null) {
            PageQuery pq = new PageQuery();
            pq.setPageNum(1);
            pq.setPageSize(300);
            skuIds = itemSkuService.queryPageList(new ItemSkuBo(), pq, keyword).getRows().stream()
                .filter(r -> r.getItemSku() != null).map(r -> r.getItemSku().getId()).toList();
            if (skuIds.isEmpty()) {
                return objectMapper.writeValueAsString(Map.of("count", 0, "items", List.of(),
                    "hint", "未匹配到商品“" + keyword + "”，可换更宽松的关键字"));
            }
        }
        int limit = limit(args, "limit", 20, 100);

        List<Map<String, Object>> rows = aiStatsMapper.selectStats(shipment, groupBy, begin, end, merchantIds, skuIds, sort, limit);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(row(r, groupBy, shipment));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("统计对象", shipment ? "出库（销售）" : "入库（采购）");
        result.put("分组", groupBy);
        result.put("日期范围", begin == null ? "全部" : fmt(begin) + " ~ " + fmt(end));
        result.put("count", out.size());
        result.put("items", out);
        if (!"none".equals(groupBy)) {
            List<Map<String, Object>> total = aiStatsMapper.selectStats(shipment, "none", begin, end, merchantIds, skuIds, "amount", 1);
            if (!total.isEmpty()) {
                result.put("合计", row(total.get(0), "none", shipment));
            }
            if (out.size() >= limit) {
                notes.add("分组数达到 limit，可能还有更多分组未列出，合计是全部范围的");
            }
        }
        notes.add("只统计已出库/已入库的单据；金额按明细金额，缺金额的按当前售价/进价补算；成本按商品当前进价");
        result.put("note", String.join("；", notes));
        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> row(Map<String, Object> r, String groupBy, boolean shipment) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!"none".equals(groupBy)) {
            m.put("分组", r.get("groupName") == null ? null : DraftSupport.clean(String.valueOf(r.get("groupName"))));
            if ("merchant".equals(groupBy) || "item".equals(groupBy) || "sku".equals(groupBy)) {
                m.put("id", r.get("groupId"));
            }
        }
        m.put("单据数", r.get("orderCount"));
        m.put("明细行数", r.get("lineCount"));
        m.put("数量", plain(toDecimal(r.get("quantity"))));
        BigDecimal amount = money(toDecimal(r.get("amount")));
        m.put("金额", amount);
        if (shipment) {
            BigDecimal cost = money(toDecimal(r.get("costAmount")));
            m.put("成本", cost);
            if (amount != null && cost != null) {
                BigDecimal margin = amount.subtract(cost);
                m.put("毛利", margin);
                if (amount.signum() > 0) {
                    m.put("毛利率", margin.multiply(BigDecimal.valueOf(100)).divide(amount, 1, RoundingMode.HALF_UP) + "%");
                }
            }
        }
        return m;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(String.valueOf(v));
    }
}
