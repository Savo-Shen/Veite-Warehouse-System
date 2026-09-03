package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.ReceiptOrderBo;
import com.ruoyi.wms.domain.bo.ShipmentOrderBo;
import com.ruoyi.wms.domain.vo.ReceiptOrderVo;
import com.ruoyi.wms.domain.vo.ShipmentOrderVo;
import com.ruoyi.wms.service.ReceiptOrderService;
import com.ruoyi.wms.service.ShipmentOrderService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：查单据列表——按单号/客户/供应商/商品关键字/日期范围/状态找出库单、入库单。
 * 返回表头（不含明细），要看明细再用 get_order。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class QueryOrdersTool implements AiTool {

    private final ShipmentOrderService shipmentOrderService;
    private final ReceiptOrderService receiptOrderService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final DraftSupport support;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> permissionsUsed() {
        return List.of(DraftSupport.PERM_SHIPMENT, DraftSupport.PERM_RECEIPT);
    }

    @Override
    public String name() {
        return "query_orders";
    }

    @Override
    public String title() {
        return "正在查单据";
    }

    @Override
    public String description() {
        return "查出库单/入库单列表：按单号片段、客户/供应商、商品关键字、日期范围、状态筛选，返回每张单的单号、对方、仓库、日期、状态、总数量、总金额。"
            + "当用户问“某客户最近的出库单”“上个月的入库单有哪些”“找一下含某商品的单子”“某天出了几单”时调用；"
            + "要看某张单的明细再用 get_order。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "orderType", enumOf("单据类型：shipment 出库单，receipt 入库单，all 两者（默认）", "shipment", "receipt", "all"),
            "keyword", string("综合关键字（可选）：可匹配单号、业务单号、备注、客户/供应商名、商品名。多个词空格分隔"),
            "orderNo", string("单号片段（可选）"),
            "merchant", string("客户或供应商名称（可选）"),
            "beginDate", string("起始日期 yyyy-MM-dd（可选，按单据业务日期）"),
            "endDate", string("截止日期 yyyy-MM-dd（可选，含当天）"),
            "status", enumOf("状态（可选）：pending 未出库/未入库，finished 已出库/已入库，invalid 已作废", "pending", "finished", "invalid"),
            "limit", integer("每类最多返回条数，默认 20，最大 50")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String type = text(args, "orderType");
        if (type == null) {
            type = "all";
        }
        String keyword = text(args, "keyword");
        String orderNo = text(args, "orderNo");
        LocalDate begin = date(args, "beginDate");
        LocalDate end = date(args, "endDate");
        Integer status = switch (text(args, "status") == null ? "" : text(args, "status")) {
            case "pending" -> 0;
            case "finished" -> 1;
            case "invalid" -> -1;
            default -> null;
        };
        int limit = limit(args, "limit", 20, 50);
        List<String> notes = new ArrayList<>();
        if ((begin == null) != (end == null)) {
            // 只给了一头：另一头放开
            if (begin == null) {
                begin = LocalDate.of(2000, 1, 1);
            } else {
                end = LocalDate.now().plusYears(1);
            }
        }

        Long merchantId = null;
        String merchant = text(args, "merchant");
        if (merchant != null) {
            List<Long> ids = orderKeywordSearcher.matchMerchantIds(merchant);
            if (ids.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("count", 0);
                m.put("items", List.of());
                m.put("hint", "没有找到叫“" + merchant + "”的往来单位，可用 search_merchant 换关键字确认全称");
                return objectMapper.writeValueAsString(m);
            }
            if (ids.size() == 1) {
                merchantId = ids.get(0);
            } else {
                // 多个同名候选，退回综合关键字（它会按名字模糊匹配全部）
                keyword = keyword == null ? merchant : keyword + " " + merchant;
                notes.add("“" + merchant + "”匹配到多个往来单位，已按名称模糊匹配全部");
            }
        }

        boolean wantShipment = !"receipt".equals(type) && AiToolContext.has(DraftSupport.PERM_SHIPMENT);
        boolean wantReceipt = !"shipment".equals(type) && AiToolContext.has(DraftSupport.PERM_RECEIPT);
        if (!wantShipment && !wantReceipt) {
            return "{\"error\":\"当前账号没有查看单据的权限\"}";
        }

        List<Map<String, Object>> out = new ArrayList<>();
        long total = 0;
        Map<Long, String> merchantCache = new HashMap<>();
        Map<Long, String> warehouseCache = new HashMap<>();

        if (wantShipment) {
            ShipmentOrderBo bo = new ShipmentOrderBo();
            bo.setKeyword(keyword);
            bo.setOrderNo(orderNo);
            bo.setMerchantId(merchantId);
            bo.setOrderStatus(status);
            Map<String, Object> params = new HashMap<>();
            if (begin != null) {
                params.put("beginTime", begin);
                params.put("endTime", end);
            }
            bo.setParams(params);
            TableDataInfo<ShipmentOrderVo> page = shipmentOrderService.queryPageList(bo, pageQuery(limit));
            total += page.getTotal();
            for (ShipmentOrderVo o : page.getRows()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("单据类型", "出库单");
                m.put("单号", o.getOrderNo());
                m.put("业务类型", shipmentType(o.getOptType()));
                m.put("客户", name(merchantCache, o.getMerchantId(), true));
                m.put("仓库", name(warehouseCache, o.getWarehouseId(), false));
                m.put("日期", fmt(o.getBizDate() != null ? o.getBizDate() : (o.getCreateTime() == null ? null : o.getCreateTime().toLocalDate())));
                m.put("状态", shipmentStatus(o.getOrderStatus()));
                m.put("总数量", plain(o.getTotalQuantity()));
                m.put("总金额", o.getTotalAmount());
                if (Boolean.TRUE.equals(o.getRecordOnly())) {
                    m.put("纯记录单", true);
                }
                m.put("备注", o.getRemark());
                m.put("_sort", o.getBizDate() == null ? LocalDate.MIN : o.getBizDate());
                out.add(m);
            }
        }
        if (wantReceipt) {
            ReceiptOrderBo bo = new ReceiptOrderBo();
            bo.setKeyword(keyword);
            bo.setOrderNo(orderNo);
            bo.setMerchantId(merchantId);
            bo.setOrderStatus(status);
            Map<String, Object> params = new HashMap<>();
            if (begin != null) {
                params.put("beginTime", begin);
                params.put("endTime", end);
            }
            bo.setParams(params);
            TableDataInfo<ReceiptOrderVo> page = receiptOrderService.queryPageList(bo, pageQuery(limit));
            total += page.getTotal();
            for (ReceiptOrderVo o : page.getRows()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("单据类型", "入库单");
                m.put("单号", o.getOrderNo());
                m.put("业务类型", receiptType(o.getOptType()));
                m.put("供应商", name(merchantCache, o.getMerchantId(), true));
                m.put("仓库", name(warehouseCache, o.getWarehouseId(), false));
                m.put("日期", fmt(o.getBizDate() != null ? o.getBizDate() : (o.getCreateTime() == null ? null : o.getCreateTime().toLocalDate())));
                m.put("状态", receiptStatus(o.getOrderStatus()));
                m.put("总数量", plain(o.getTotalQuantity()));
                m.put("总金额", o.getTotalAmount());
                m.put("备注", o.getRemark());
                m.put("_sort", o.getBizDate() == null ? LocalDate.MIN : o.getBizDate());
                out.add(m);
            }
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> (LocalDate) m.get("_sort")).reversed());
        out.forEach(m -> m.remove("_sort"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("total", total);
        result.put("items", out);
        if (total > out.size()) {
            notes.add("符合条件的共 " + total + " 张，只列出了最近 " + out.size() + " 张；可加日期/客户条件缩小范围或调大 limit");
        }
        if (out.isEmpty()) {
            notes.add("没有符合条件的单据。可放宽日期范围、换更短的关键字，或去掉状态过滤再试");
        }
        if (!notes.isEmpty()) {
            result.put("hint", String.join("；", notes));
        }
        return objectMapper.writeValueAsString(result);
    }

    private static PageQuery pageQuery(int limit) {
        PageQuery pq = new PageQuery();
        pq.setPageNum(1);
        pq.setPageSize(limit);
        return pq;
    }

    private String name(Map<Long, String> cache, Long id, boolean merchant) {
        if (id == null) {
            return null;
        }
        return cache.computeIfAbsent(id, k -> merchant ? support.merchantName(k) : support.warehouseName(k));
    }
}
