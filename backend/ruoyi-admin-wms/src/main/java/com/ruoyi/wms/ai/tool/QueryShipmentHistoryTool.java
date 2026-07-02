package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.entity.ShipmentOrder;
import com.ruoyi.wms.domain.entity.ShipmentOrderDetail;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.domain.vo.MerchantVo;
import com.ruoyi.wms.mapper.ShipmentOrderDetailMapper;
import com.ruoyi.wms.mapper.ShipmentOrderMapper;
import com.ruoyi.wms.service.ItemSkuService;
import com.ruoyi.wms.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具：按商品查“出库历史”——这个商品之前出库给了谁、什么时候、多少、单价。
 * 复用商品综合搜索定位 sku，再反查出库单明细。只读。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class QueryShipmentHistoryTool implements AiTool {

    private final ItemSkuService itemSkuService;
    private final ShipmentOrderDetailMapper shipmentOrderDetailMapper;
    private final ShipmentOrderMapper shipmentOrderMapper;
    private final MerchantService merchantService;
    private final ObjectMapper objectMapper;

    /** 单次返回的出库明细行数上限 */
    private static final int LIMIT = 20;

    @Override
    public String name() {
        return "query_shipment_history";
    }

    @Override
    public String description() {
        return "按商品查询出库历史：某商品之前出库给了哪些客户、什么时间、数量、单价。"
            + "当用户问“这个商品之前卖给谁/出给谁、什么时候出过、出过多少”时调用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "商品或规格的名称、编号、条码关键字（用于定位是哪个商品）");

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

        // 1. 用综合搜索定位匹配的 sku（继承分词/多词逻辑）
        PageQuery skuPage = new PageQuery();
        skuPage.setPageNum(1);
        skuPage.setPageSize(50);
        List<ItemSkuMapVo> skuRows = itemSkuService.queryPageList(new ItemSkuBo(), skuPage, keyword).getRows();
        if (skuRows.isEmpty()) {
            return emptyResult("未匹配到商品。可换更宽松的关键字（只用商品名或只用尺寸）再查。");
        }
        Map<Long, ItemSkuMapVo> skuMap = skuRows.stream()
            .filter(r -> r.getItemSku() != null)
            .collect(Collectors.toMap(r -> r.getItemSku().getId(), r -> r, (a, b) -> a));
        Collection<Long> skuIds = skuMap.keySet();
        if (skuIds.isEmpty()) {
            return emptyResult("未匹配到商品规格。");
        }

        // 2. 反查出库单明细（按时间倒序，取最近若干条）
        List<ShipmentOrderDetail> details = shipmentOrderDetailMapper.selectList(
            Wrappers.<ShipmentOrderDetail>lambdaQuery()
                .in(ShipmentOrderDetail::getSkuId, skuIds)
                .orderByDesc(ShipmentOrderDetail::getId)
                .last("limit " + LIMIT));
        if (details.isEmpty()) {
            return emptyResult("匹配到了商品，但没有它的出库记录。");
        }

        // 3. 批量取出库单头（客户、单号、状态、时间）
        List<Long> orderIds = details.stream().map(ShipmentOrderDetail::getOrderId).distinct().toList();
        Map<Long, ShipmentOrder> orderMap = shipmentOrderMapper.selectBatchIds(orderIds).stream()
            .collect(Collectors.toMap(ShipmentOrder::getId, o -> o, (a, b) -> a));

        // 4. 组装结果
        Map<Long, String> merchantCache = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ShipmentOrderDetail d : details) {
            ShipmentOrder order = orderMap.get(d.getOrderId());
            ItemSkuMapVo sku = skuMap.get(d.getSkuId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("出库单号", order == null ? null : order.getOrderNo());
            m.put("客户", order == null ? null : merchantName(merchantCache, order.getMerchantId()));
            m.put("状态", order == null ? null : statusText(order.getOrderStatus()));
            m.put("出库时间", order == null ? null : fmt(order.getCreateTime()));
            m.put("商品", sku == null || sku.getItem() == null ? null : sku.getItem().getItemName());
            m.put("规格", sku == null || sku.getItemSku() == null ? null : sku.getItemSku().getSkuName());
            m.put("数量", d.getQuantity());
            m.put("金额", d.getAmount());
            out.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("note", "按出库时间倒序，最多 " + LIMIT + " 条");
        result.put("items", out);
        return objectMapper.writeValueAsString(result);
    }

    private String emptyResult(String hint) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", 0);
        result.put("items", List.of());
        result.put("hint", hint);
        return objectMapper.writeValueAsString(result);
    }

    private String merchantName(Map<Long, String> cache, Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        return cache.computeIfAbsent(merchantId, id -> {
            MerchantVo vo = merchantService.queryById(id);
            return vo == null ? null : vo.getMerchantName();
        });
    }

    private String statusText(Integer s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case 1 -> "已出库";
            case 0 -> "未出库";
            case -1 -> "已作废";
            default -> String.valueOf(s);
        };
    }

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String fmt(LocalDateTime d) {
        return d == null ? null : d.format(DTF);
    }
}
