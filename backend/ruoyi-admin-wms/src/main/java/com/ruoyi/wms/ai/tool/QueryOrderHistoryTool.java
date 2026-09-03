package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.entity.ReceiptOrder;
import com.ruoyi.wms.domain.entity.ReceiptOrderDetail;
import com.ruoyi.wms.domain.entity.ShipmentOrder;
import com.ruoyi.wms.domain.entity.ShipmentOrderDetail;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.mapper.ReceiptOrderDetailMapper;
import com.ruoyi.wms.mapper.ReceiptOrderMapper;
import com.ruoyi.wms.mapper.ShipmentOrderDetailMapper;
import com.ruoyi.wms.mapper.ShipmentOrderMapper;
import com.ruoyi.wms.service.ItemSkuService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：查出入库明细历史——某商品之前卖给了谁/从谁进的、某客户买过什么、某段时间的出入库记录，逐行带数量、单价、金额。
 * 只读。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class QueryOrderHistoryTool implements AiTool {

    private final ItemSkuService itemSkuService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final ShipmentOrderMapper shipmentOrderMapper;
    private final ShipmentOrderDetailMapper shipmentOrderDetailMapper;
    private final ReceiptOrderMapper receiptOrderMapper;
    private final ReceiptOrderDetailMapper receiptOrderDetailMapper;
    private final DraftSupport support;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> permissionsUsed() {
        return List.of(DraftSupport.PERM_SHIPMENT, DraftSupport.PERM_RECEIPT);
    }

    @Override
    public String name() {
        return "query_order_history";
    }

    @Override
    public String title() {
        return "正在查出入库记录";
    }

    @Override
    public String description() {
        return "查出入库明细记录（逐行）：某商品之前出库给了哪些客户/从哪个供应商进过、什么时间、数量、单价、金额；"
            + "某客户买过哪些东西、上次的价格；某段时间内的出入库明细。"
            + "商品关键字、客户/供应商、日期范围至少给一个。当用户问“这个商品之前卖给谁/什么价”“约克上个月买了什么”"
            + "“最近进了什么货”时调用。结果里带 skuId，可直接用于建单。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "orderType", enumOf("shipment 出库记录（默认），receipt 入库记录", "shipment", "receipt"),
            "keyword", string("商品或规格关键字（可选）"),
            "merchant", string("客户或供应商名称（可选）"),
            "beginDate", string("起始日期 yyyy-MM-dd（可选）"),
            "endDate", string("截止日期 yyyy-MM-dd（可选，含当天）"),
            "limit", integer("最多返回明细行数，默认 20，最大 100")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        boolean shipment = !"receipt".equals(text(args, "orderType"));
        String perm = shipment ? DraftSupport.PERM_SHIPMENT : DraftSupport.PERM_RECEIPT;
        if (!AiToolContext.has(perm)) {
            return "{\"error\":\"当前账号没有查看" + (shipment ? "出库" : "入库") + "记录的权限\"}";
        }
        String keyword = text(args, "keyword");
        String merchant = text(args, "merchant");
        LocalDate begin = date(args, "beginDate");
        LocalDate end = date(args, "endDate");
        int limit = limit(args, "limit", 20, 100);
        if (keyword == null && merchant == null && begin == null && end == null) {
            return "{\"error\":\"商品关键字、客户/供应商、日期范围至少给一个\"}";
        }
        if (begin == null && end != null) {
            begin = LocalDate.of(2000, 1, 1);
        }
        if (begin != null && end == null) {
            end = LocalDate.now().plusYears(1);
        }

        // 1. 商品 → skuIds
        Set<Long> skuFilter = null;
        if (keyword != null) {
            PageQuery pq = new PageQuery();
            pq.setPageNum(1);
            pq.setPageSize(50);
            skuFilter = itemSkuService.queryPageList(new ItemSkuBo(), pq, keyword).getRows().stream()
                .filter(r -> r.getItemSku() != null).map(r -> r.getItemSku().getId()).collect(Collectors.toSet());
            if (skuFilter.isEmpty()) {
                return empty("未匹配到商品“" + keyword + "”。可换更宽松的关键字（只用商品名或只用规格）再查");
            }
        }
        // 2. 客户/供应商 → merchantIds
        List<Long> merchantIds = null;
        if (merchant != null) {
            merchantIds = orderKeywordSearcher.matchMerchantIds(merchant);
            if (merchantIds.isEmpty()) {
                return empty("没有找到叫“" + merchant + "”的往来单位，可用 search_merchant 确认全称");
            }
        }
        // 3. 按客户/日期先圈出单据（作废的不算历史）
        List<Long> orderFilter = null;
        Map<Long, Object> orderMap = new HashMap<>();
        if (merchantIds != null || begin != null) {
            if (shipment) {
                var lqw = Wrappers.<ShipmentOrder>lambdaQuery().ne(ShipmentOrder::getOrderStatus, -1);
                if (merchantIds != null) {
                    lqw.in(ShipmentOrder::getMerchantId, merchantIds);
                }
                if (begin != null) {
                    lqw.between(ShipmentOrder::getBizDate, begin, end);
                }
                List<ShipmentOrder> orders = shipmentOrderMapper.selectList(lqw);
                orders.forEach(o -> orderMap.put(o.getId(), o));
                orderFilter = new ArrayList<>(orderMap.keySet());
            } else {
                var lqw = Wrappers.<ReceiptOrder>lambdaQuery().ne(ReceiptOrder::getOrderStatus, -1);
                if (merchantIds != null) {
                    lqw.in(ReceiptOrder::getMerchantId, merchantIds);
                }
                if (begin != null) {
                    lqw.between(ReceiptOrder::getBizDate, begin, end);
                }
                List<ReceiptOrder> orders = receiptOrderMapper.selectList(lqw);
                orders.forEach(o -> orderMap.put(o.getId(), o));
                orderFilter = new ArrayList<>(orderMap.keySet());
            }
            if (orderFilter.isEmpty()) {
                return empty("这个范围内没有" + (shipment ? "出库" : "入库") + "单");
            }
        }

        // 4. 明细
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal sumQty = BigDecimal.ZERO;
        BigDecimal sumAmount = BigDecimal.ZERO;
        Map<Long, String> merchantCache = new HashMap<>();
        if (shipment) {
            var lqw = Wrappers.<ShipmentOrderDetail>lambdaQuery();
            if (orderFilter != null) {
                lqw.in(ShipmentOrderDetail::getOrderId, orderFilter);
            }
            if (skuFilter != null) {
                lqw.in(ShipmentOrderDetail::getSkuId, skuFilter);
            }
            lqw.orderByDesc(ShipmentOrderDetail::getId).last("limit " + limit);
            List<ShipmentOrderDetail> details = shipmentOrderDetailMapper.selectList(lqw);
            if (details.isEmpty()) {
                return empty("匹配到了商品/客户，但没有对应的出库记录");
            }
            // 补单头
            List<Long> missing = details.stream().map(ShipmentOrderDetail::getOrderId).distinct()
                .filter(id -> !orderMap.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                shipmentOrderMapper.selectBatchIds(missing).forEach(o -> orderMap.put(o.getId(), o));
            }
            Map<Long, ItemSkuMapVo> skuMap = skuMap(details.stream().map(ShipmentOrderDetail::getSkuId).collect(Collectors.toSet()));
            for (ShipmentOrderDetail d : details) {
                ShipmentOrder o = (ShipmentOrder) orderMap.get(d.getOrderId());
                if (o != null && Integer.valueOf(-1).equals(o.getOrderStatus())) {
                    continue;
                }
                ItemSkuMapVo sku = d.getSkuId() == null ? null : skuMap.get(d.getSkuId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("单号", o == null ? null : o.getOrderNo());
                m.put("日期", o == null ? null : fmt(o.getBizDate() != null ? o.getBizDate() : o.getCreateTime().toLocalDate()));
                m.put("客户", o == null ? null : merchantCache.computeIfAbsent(o.getMerchantId() == null ? -1L : o.getMerchantId(),
                    k -> k < 0 ? null : support.merchantName(k)));
                m.put("状态", o == null ? null : shipmentStatus(o.getOrderStatus()));
                m.put("商品", sku != null && sku.getItem() != null ? sku.getItem().getItemName() : d.getItemName());
                m.put("规格", sku == null || sku.getItemSku() == null ? null : sku.getItemSku().getSkuName());
                m.put("单位", sku == null || sku.getItem() == null ? null : sku.getItem().getUnit());
                m.put("数量", plain(d.getQuantity()));
                m.put("单价", d.getSalePrice() != null ? d.getSalePrice() : unitPrice(d.getAmount(), d.getQuantity()));
                m.put("金额", d.getAmount());
                m.put("skuId", d.getSkuId());
                out.add(m);
                if (d.getQuantity() != null) {
                    sumQty = sumQty.add(d.getQuantity());
                }
                if (d.getAmount() != null) {
                    sumAmount = sumAmount.add(d.getAmount());
                }
            }
        } else {
            var lqw = Wrappers.<ReceiptOrderDetail>lambdaQuery();
            if (orderFilter != null) {
                lqw.in(ReceiptOrderDetail::getOrderId, orderFilter);
            }
            if (skuFilter != null) {
                lqw.in(ReceiptOrderDetail::getSkuId, skuFilter);
            }
            lqw.orderByDesc(ReceiptOrderDetail::getId).last("limit " + limit);
            List<ReceiptOrderDetail> details = receiptOrderDetailMapper.selectList(lqw);
            if (details.isEmpty()) {
                return empty("匹配到了商品/供应商，但没有对应的入库记录");
            }
            List<Long> missing = details.stream().map(ReceiptOrderDetail::getOrderId).distinct()
                .filter(id -> !orderMap.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                receiptOrderMapper.selectBatchIds(missing).forEach(o -> orderMap.put(o.getId(), o));
            }
            Map<Long, ItemSkuMapVo> skuMap = skuMap(details.stream().map(ReceiptOrderDetail::getSkuId).collect(Collectors.toSet()));
            for (ReceiptOrderDetail d : details) {
                ReceiptOrder o = (ReceiptOrder) orderMap.get(d.getOrderId());
                if (o != null && Integer.valueOf(-1).equals(o.getOrderStatus())) {
                    continue;
                }
                ItemSkuMapVo sku = d.getSkuId() == null ? null : skuMap.get(d.getSkuId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("单号", o == null ? null : o.getOrderNo());
                m.put("日期", o == null ? null : fmt(o.getBizDate() != null ? o.getBizDate() : o.getCreateTime().toLocalDate()));
                m.put("供应商", o == null ? null : merchantCache.computeIfAbsent(o.getMerchantId() == null ? -1L : o.getMerchantId(),
                    k -> k < 0 ? null : support.merchantName(k)));
                m.put("状态", o == null ? null : receiptStatus(o.getOrderStatus()));
                m.put("商品", sku == null || sku.getItem() == null ? null : sku.getItem().getItemName());
                m.put("规格", sku == null || sku.getItemSku() == null ? null : sku.getItemSku().getSkuName());
                m.put("单位", sku == null || sku.getItem() == null ? null : sku.getItem().getUnit());
                m.put("数量", plain(d.getQuantity()));
                m.put("单价", unitPrice(d.getAmount(), d.getQuantity()));
                m.put("金额", d.getAmount());
                m.put("skuId", d.getSkuId());
                out.add(m);
                if (d.getQuantity() != null) {
                    sumQty = sumQty.add(d.getQuantity());
                }
                if (d.getAmount() != null) {
                    sumAmount = sumAmount.add(d.getAmount());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("note", "按时间倒序，最多 " + limit + " 行" + (out.size() >= limit ? "（可能还有更早的记录，可加日期范围或调大 limit）" : ""));
        result.put("items", out);
        result.put("列出行合计数量", plain(sumQty));
        result.put("列出行合计金额", money(sumAmount));
        return objectMapper.writeValueAsString(result);
    }

    private Map<Long, ItemSkuMapVo> skuMap(Set<Long> skuIds) {
        Set<Long> ids = new HashSet<>(skuIds);
        ids.remove(null);
        return ids.isEmpty() ? Map.of() : itemSkuService.queryItemSkuMapVosByIds(ids);
    }

    private String empty(String hint) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", 0);
        result.put("items", List.of());
        result.put("hint", hint);
        return objectMapper.writeValueAsString(result);
    }
}
