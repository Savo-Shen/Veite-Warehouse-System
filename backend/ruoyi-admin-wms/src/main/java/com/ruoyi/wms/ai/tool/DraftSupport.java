package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.entity.ReceiptOrder;
import com.ruoyi.wms.domain.entity.ShipmentOrder;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.domain.vo.ItemSkuVo;
import com.ruoyi.wms.domain.vo.ItemVo;
import com.ruoyi.wms.domain.vo.MerchantVo;
import com.ruoyi.wms.domain.vo.ReceiptOrderDetailVo;
import com.ruoyi.wms.domain.vo.ReceiptOrderVo;
import com.ruoyi.wms.domain.vo.ShipmentOrderDetailVo;
import com.ruoyi.wms.domain.vo.ShipmentOrderVo;
import com.ruoyi.wms.domain.vo.SkuLastPriceVo;
import com.ruoyi.wms.domain.vo.WarehouseVo;
import com.ruoyi.wms.mapper.ReceiptOrderMapper;
import com.ruoyi.wms.mapper.ShipmentOrderMapper;
import com.ruoyi.wms.service.ItemSkuService;
import com.ruoyi.wms.service.MerchantService;
import com.ruoyi.wms.service.ReceiptOrderService;
import com.ruoyi.wms.service.ShipmentOrderDetailService;
import com.ruoyi.wms.service.ShipmentOrderService;
import com.ruoyi.wms.service.WarehouseService;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ruoyi.wms.ai.tool.ToolSupport.fmt;
import static com.ruoyi.wms.ai.tool.ToolSupport.plain;
import static com.ruoyi.wms.ai.tool.ToolSupport.unitPrice;

/**
 * 建单/改单草稿的公共逻辑：按单号调单、匹配客户/仓库/商品、算单价、汇总、给模型的精简摘要。
 * <p>
 * 出库草稿、入库草稿、改单草稿三个工具共用，避免同一套“匹配商品 + 单价模式”写三遍。
 *
 * @author Savo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftSupport {

    public static final String PERM_SHIPMENT = "wms:shipment:all";
    public static final String PERM_RECEIPT = "wms:receipt:all";

    private final ItemSkuService itemSkuService;
    private final MerchantService merchantService;
    private final WarehouseService warehouseService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final ShipmentOrderMapper shipmentOrderMapper;
    private final ReceiptOrderMapper receiptOrderMapper;
    private final ShipmentOrderService shipmentOrderService;
    private final ReceiptOrderService receiptOrderService;
    private final ShipmentOrderDetailService shipmentOrderDetailService;
    private final ObjectMapper objectMapper;

    /* ================= 数据结构 ================= */

    /** 草稿：前端出库/入库编辑页能直接预填的结构 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Draft {
        /** shipment / receipt */
        private String type;
        /** new = 新建一张；edit = 修改已有单据（orderId 指向它） */
        private String mode = "new";
        private Long orderId;
        private String orderNo;
        /** 复制来源单号 */
        private String sourceOrderNo;
        private Long merchantId;
        private String merchantName;
        private Long warehouseId;
        private String warehouseName;
        private String optType;
        private String bizOrderNo;
        private String bizDate;
        private String remark;
        private Boolean recordOnly;
        private String priceMode;
        private List<DraftLine> details = new ArrayList<>();
        private List<Map<String, Object>> unresolved = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private int resolvedCount;
        private BigDecimal totalQuantity;
        private BigDecimal totalAmount;
    }

    /** 草稿的一行明细 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DraftLine {
        /** 修改已有单据时指向原明细行 id；新增行为空 */
        private Long id;
        private Long skuId;
        private ItemVo item;
        private ItemSkuVo itemSku;
        /** 纯记录单的行没有 sku，只有名字 */
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal price;
        /** 出库单落库用的销售价，与 price 同值，前端还原单价时优先读它 */
        private BigDecimal salePrice;
        private BigDecimal amount;
        private String remark;
        private Long warehouseId;
        /** 复制来源/原单上的单价（中间量，不输出） */
        @JsonIgnore
        private BigDecimal sourcePrice;
        /** 用户明确指定的单价（中间量，不输出） */
        @JsonIgnore
        private BigDecimal explicitPrice;
    }

    /** 按单号调出来的已有单据（出库或入库），供复制/修改/查看 */
    @Data
    public static class SourceOrder {
        private String type;
        private Long id;
        private String orderNo;
        private Integer status;
        private Boolean recordOnly;
        private Long merchantId;
        private String merchantName;
        private Long warehouseId;
        private String warehouseName;
        private Long optType;
        private String bizOrderNo;
        private LocalDate bizDate;
        private LocalDateTime createTime;
        private String remark;
        private BigDecimal totalQuantity;
        private BigDecimal totalAmount;
        private List<SourceLine> lines = new ArrayList<>();

        public boolean isShipment() {
            return "shipment".equals(type);
        }

        /** 明细还能不能改：出库单要未出库或纯记录单，入库单要未入库 */
        public boolean isEditable() {
            if (isShipment()) {
                return Integer.valueOf(0).equals(status) || Boolean.TRUE.equals(recordOnly);
            }
            return Integer.valueOf(0).equals(status);
        }

        public String editableReason() {
            if (isEditable()) {
                return Boolean.TRUE.equals(recordOnly) ? "纯记录单，可反复修改" : "尚未" + (isShipment() ? "出库" : "入库") + "，可修改";
            }
            String st = isShipment() ? ToolSupport.shipmentStatus(status) : ToolSupport.receiptStatus(status);
            return st + "的单据明细已锁定（库存已按它变动过），只能补备注/照片；要按新价格或新数量再来一张，请复制成新单";
        }
    }

    @Data
    public static class SourceLine {
        private Long id;
        private Long skuId;
        private ItemVo item;
        private ItemSkuVo itemSku;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal amount;
        private BigDecimal price;
        private String remark;
        private Long warehouseId;
    }

    /** 商品匹配结果 */
    public record SkuPick(ItemSkuMapVo picked, boolean found, boolean exact, String warning, String reason) {
        static SkuPick notFound(String reason) {
            return new SkuPick(null, false, false, null, reason);
        }
    }

    /* ================= 单据 ================= */

    /**
     * 按单号调出出库单或入库单（带明细、商品信息）。
     * 先精确匹配，再模糊匹配（用户可能只说了后几位）；没权限看的单据类型直接跳过。
     */
    public SourceOrder loadOrder(String orderNo, String preferType) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        String no = orderNo.trim();
        boolean tryShipment = AiToolContext.has(PERM_SHIPMENT) && !"receipt".equals(preferType);
        boolean tryReceipt = AiToolContext.has(PERM_RECEIPT) && !"shipment".equals(preferType);

        if (tryShipment) {
            ShipmentOrder o = shipmentOrderMapper.selectOne(Wrappers.<ShipmentOrder>lambdaQuery()
                .eq(ShipmentOrder::getOrderNo, no).last("limit 1"));
            if (o == null) {
                List<ShipmentOrder> like = shipmentOrderMapper.selectList(Wrappers.<ShipmentOrder>lambdaQuery()
                    .like(ShipmentOrder::getOrderNo, no).last("limit 2"));
                o = like.size() == 1 ? like.get(0) : null;
            }
            if (o != null) {
                return fromShipment(shipmentOrderService.queryById(o.getId()));
            }
        }
        if (tryReceipt) {
            ReceiptOrder o = receiptOrderMapper.selectOne(Wrappers.<ReceiptOrder>lambdaQuery()
                .eq(ReceiptOrder::getOrderNo, no).last("limit 1"));
            if (o == null) {
                List<ReceiptOrder> like = receiptOrderMapper.selectList(Wrappers.<ReceiptOrder>lambdaQuery()
                    .like(ReceiptOrder::getOrderNo, no).last("limit 2"));
                o = like.size() == 1 ? like.get(0) : null;
            }
            if (o != null) {
                return fromReceipt(receiptOrderService.queryById(o.getId()));
            }
        }
        return null;
    }

    private SourceOrder fromShipment(ShipmentOrderVo vo) {
        SourceOrder s = new SourceOrder();
        s.setType("shipment");
        s.setId(vo.getId());
        s.setOrderNo(vo.getOrderNo());
        s.setStatus(vo.getOrderStatus());
        s.setRecordOnly(Boolean.TRUE.equals(vo.getRecordOnly()));
        s.setMerchantId(vo.getMerchantId());
        s.setMerchantName(merchantName(vo.getMerchantId()));
        s.setWarehouseId(vo.getWarehouseId());
        s.setWarehouseName(warehouseName(vo.getWarehouseId()));
        s.setOptType(vo.getOptType());
        s.setBizOrderNo(vo.getBizOrderNo());
        s.setBizDate(vo.getBizDate());
        s.setCreateTime(vo.getCreateTime());
        s.setRemark(vo.getRemark());
        s.setTotalQuantity(vo.getTotalQuantity());
        s.setTotalAmount(vo.getTotalAmount());
        if (vo.getDetails() != null) {
            for (ShipmentOrderDetailVo d : vo.getDetails()) {
                SourceLine l = new SourceLine();
                l.setId(d.getId());
                l.setSkuId(d.getSkuId());
                l.setItem(d.getItem());
                l.setItemSku(d.getItemSku());
                l.setItemName(d.getItemName() != null ? d.getItemName()
                    : (d.getItem() == null ? null : d.getItem().getItemName()));
                l.setQuantity(d.getQuantity());
                l.setAmount(d.getAmount());
                l.setPrice(d.getSalePrice() != null ? d.getSalePrice() : unitPrice(d.getAmount(), d.getQuantity()));
                l.setRemark(d.getRemark());
                l.setWarehouseId(d.getWarehouseId());
                s.getLines().add(l);
            }
        }
        return s;
    }

    private SourceOrder fromReceipt(ReceiptOrderVo vo) {
        SourceOrder s = new SourceOrder();
        s.setType("receipt");
        s.setId(vo.getId());
        s.setOrderNo(vo.getOrderNo());
        s.setStatus(vo.getOrderStatus());
        s.setRecordOnly(false);
        s.setMerchantId(vo.getMerchantId());
        s.setMerchantName(merchantName(vo.getMerchantId()));
        s.setWarehouseId(vo.getWarehouseId());
        s.setWarehouseName(warehouseName(vo.getWarehouseId()));
        s.setOptType(vo.getOptType());
        s.setBizOrderNo(vo.getBizOrderNo());
        s.setBizDate(vo.getBizDate());
        s.setCreateTime(vo.getCreateTime());
        s.setRemark(vo.getRemark());
        s.setTotalQuantity(vo.getTotalQuantity());
        s.setTotalAmount(vo.getTotalAmount());
        if (vo.getDetails() != null) {
            for (ReceiptOrderDetailVo d : vo.getDetails()) {
                SourceLine l = new SourceLine();
                l.setId(d.getId());
                l.setSkuId(d.getSkuId());
                l.setItem(d.getItem());
                l.setItemSku(d.getItemSku());
                l.setItemName(d.getItem() == null ? null : d.getItem().getItemName());
                l.setQuantity(d.getQuantity());
                l.setAmount(d.getAmount());
                l.setPrice(unitPrice(d.getAmount(), d.getQuantity()));
                l.setRemark(d.getRemark());
                l.setWarehouseId(d.getWarehouseId());
                s.getLines().add(l);
            }
        }
        return s;
    }

    public String merchantName(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        MerchantVo m = merchantService.queryById(merchantId);
        return m == null ? null : clean(m.getMerchantName());
    }

    /** 老数据里有些名称带着从 Excel 粘进来的 \r 和首尾空格，输出前去掉 */
    public static String clean(String s) {
        return s == null ? null : s.strip();
    }

    public String warehouseName(Long warehouseId) {
        if (warehouseId == null) {
            return null;
        }
        WarehouseVo w = warehouseService.queryById(warehouseId);
        return w == null ? null : w.getWarehouseName();
    }

    /* ================= 客户 / 仓库 / 商品匹配 ================= */

    /**
     * 按名称/编号匹配往来单位并写进草稿。expectedType：1 客户 / 2 供应商，用于多个命中时优先挑对类型的。
     */
    public void resolveMerchant(Draft d, String keyword, Integer expectedType, String label) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        List<Long> ids = orderKeywordSearcher.matchMerchantIds(keyword.trim());
        if (ids.isEmpty()) {
            d.getWarnings().add("未找到" + label + "“" + keyword + "”，请在表单中手动选择" + label + "。");
            return;
        }
        List<MerchantVo> hits = new ArrayList<>();
        for (Long id : ids) {
            MerchantVo m = merchantService.queryById(id);
            if (m != null) {
                hits.add(m);
            }
        }
        if (hits.isEmpty()) {
            d.getWarnings().add("未找到" + label + "“" + keyword + "”，请在表单中手动选择" + label + "。");
            return;
        }
        MerchantVo picked = hits.stream()
            .filter(m -> expectedType == null || expectedType.equals(m.getMerchantType()))
            .findFirst().orElse(hits.get(0));
        d.setMerchantId(picked.getId());
        d.setMerchantName(clean(picked.getMerchantName()));
        if (hits.size() > 1) {
            String others = hits.stream().limit(5).map(m -> clean(m.getMerchantName())).collect(Collectors.joining("、"));
            d.getWarnings().add(label + "“" + keyword + "”匹配到多个（" + others + "），已选“"
                + picked.getMerchantName() + "”，请确认。");
        }
    }

    public void resolveWarehouse(Draft d, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        List<Long> ids = orderKeywordSearcher.matchWarehouseIds(keyword.trim());
        if (ids.isEmpty()) {
            d.getWarnings().add("未找到仓库“" + keyword + "”，将使用默认仓库，请确认。");
            return;
        }
        WarehouseVo w = warehouseService.queryById(ids.get(0));
        if (w != null) {
            d.setWarehouseId(w.getId());
            d.setWarehouseName(w.getWarehouseName());
        }
    }

    /**
     * 定位商品规格：有 skuId 直接按 id 取（精确）；否则按名字搜，再按单位挑计量方式。
     */
    public SkuPick resolveSku(Long skuId, String name, String unit) {
        if (skuId != null) {
            ItemSkuMapVo vo = itemSkuService.queryItemSkuMapVosByIds(Set.of(skuId)).get(skuId);
            if (vo != null && vo.getItemSku() != null) {
                return new SkuPick(vo, true, true, null, null);
            }
            if (name == null || name.isBlank()) {
                return SkuPick.notFound("skuId " + skuId + " 不存在");
            }
        }
        if (name == null || name.isBlank()) {
            return SkuPick.notFound("没有商品名");
        }
        PageQuery pq = new PageQuery();
        pq.setPageNum(1);
        pq.setPageSize(5);
        TableDataInfo<ItemSkuMapVo> page = itemSkuService.queryPageList(new ItemSkuBo(), pq, name);
        List<ItemSkuMapVo> rows = page.getRows().stream().filter(r -> r.getItemSku() != null).toList();
        if (rows.isEmpty()) {
            return SkuPick.notFound("未找到商品");
        }
        ItemSkuMapVo picked = AiTool.pickByUnit(rows, unit);
        String warning = null;
        if (rows.size() > 1) {
            boolean unitMatched = unit != null && picked.getItem() != null
                && AiTool.sameUnitGroup(picked.getItem().getUnit(), unit);
            String candidates = rows.stream().limit(5).map(DraftSupport::shortName).collect(Collectors.joining("；"));
            String more = page.getTotal() > rows.size() ? "…等共 " + page.getTotal() + " 个" : "";
            if (unitMatched) {
                warning = "“" + name + "”按单位「" + unit + "」选中：" + shortName(picked) + "，其它候选：" + candidates + more + "，请确认。";
            } else {
                warning = "“" + name + "”匹配到多个（" + candidates + more + "），已选“" + shortName(picked) + "”，请确认是否是想要的。";
            }
        }
        return new SkuPick(picked, true, rows.size() == 1, warning, null);
    }

    public static String shortName(ItemSkuMapVo vo) {
        String item = vo.getItem() == null ? "" : vo.getItem().getItemName();
        String sku = vo.getItemSku() == null ? "" : vo.getItemSku().getSkuName();
        String unit = vo.getItem() == null || vo.getItem().getUnit() == null ? "" : "/" + vo.getItem().getUnit();
        return item + " " + sku + unit;
    }

    /* ================= 单价 ================= */

    /**
     * 把用户口语里的单价说法归一成模式名。
     */
    public static String normalizePriceMode(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.trim().toLowerCase();
        return switch (t) {
            case "cost", "进价", "成本", "成本价", "采购价" -> "cost";
            case "selling", "sell", "售价", "销售价", "零售价" -> "selling";
            case "last", "上次", "上次价", "上次成交价", "老价格", "历史价" -> "last";
            case "source", "原价", "原单价", "来源单价" -> "source";
            case "zero", "0", "零", "免费" -> "zero";
            case "keep", "不变", "保持" -> "keep";
            default -> t;
        };
    }

    /**
     * 按单价模式算一行的单价。
     *
     * @param explicit    用户明确给的单价，优先级最高
     * @param sourcePrice 复制来源/原单上的单价
     * @param lastPrice   该客户上次成交价
     * @param shipment    出库单默认售价，入库单默认进价
     */
    public BigDecimal priceFor(ItemSkuVo sku, String priceMode, BigDecimal explicit,
                               BigDecimal sourcePrice, BigDecimal lastPrice, boolean shipment) {
        if (explicit != null) {
            return explicit;
        }
        BigDecimal def = sku == null ? null : (shipment ? sku.getSellingPrice() : sku.getCostPrice());
        String mode = priceMode == null ? (sourcePrice != null ? "source" : "default") : priceMode;
        return switch (mode) {
            case "cost" -> sku == null ? null : sku.getCostPrice();
            case "selling" -> sku == null ? null : sku.getSellingPrice();
            case "source", "keep" -> sourcePrice != null ? sourcePrice : def;
            case "last" -> lastPrice != null ? lastPrice : def;
            case "zero" -> BigDecimal.ZERO;
            default -> def;
        };
    }

    public static String priceModeLabel(String mode, boolean shipment) {
        if (mode == null) {
            return shipment ? "售价" : "进价";
        }
        return switch (mode) {
            case "cost" -> "进价";
            case "selling" -> "售价";
            case "source" -> "来源单上的单价";
            case "keep" -> "原单价";
            case "last" -> "该客户上次成交价";
            case "zero" -> "0";
            default -> shipment ? "售价" : "进价";
        };
    }

    /** 客户对每个规格的最近一次成交价 */
    public Map<Long, BigDecimal> lastPrices(Long merchantId, Collection<Long> skuIds) {
        if (merchantId == null || skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> out = new HashMap<>();
        for (SkuLastPriceVo v : shipmentOrderDetailService.queryLastPrices(merchantId, new ArrayList<>(skuIds))) {
            if (v.getPrice() != null) {
                out.put(v.getSkuId(), v.getPrice());
            }
        }
        return out;
    }

    /* ================= 汇总 / 输出 ================= */

    /** 算每行金额和整单合计，补 resolvedCount */
    public void finish(Draft d) {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        boolean anyAmount = false;
        for (DraftLine l : d.getDetails()) {
            if (l.getQuantity() != null && l.getPrice() != null) {
                l.setAmount(l.getPrice().multiply(l.getQuantity()).setScale(2, RoundingMode.HALF_UP));
            } else if (l.getPrice() == null) {
                l.setAmount(null);
            }
            if ("shipment".equals(d.getType())) {
                l.setSalePrice(l.getPrice());
            }
            if (l.getWarehouseId() == null) {
                l.setWarehouseId(d.getWarehouseId());
            }
            if (l.getQuantity() != null) {
                qty = qty.add(l.getQuantity());
            }
            if (l.getAmount() != null) {
                amount = amount.add(l.getAmount());
                anyAmount = true;
            }
        }
        d.setResolvedCount(d.getDetails().size());
        d.setTotalQuantity(plain(qty));
        d.setTotalAmount(anyAmount ? amount.setScale(2, RoundingMode.HALF_UP) : null);
    }

    public String toJson(Draft d) {
        try {
            return objectMapper.writeValueAsString(d);
        } catch (Exception e) {
            throw new IllegalStateException("草稿序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 给模型看的精简版：去掉整块 item/itemSku 对象，只留名字、数量、单价、金额。
     * 不是草稿（比如错误信息）就原样返回。
     */
    public String summarize(String draftJson) {
        if (draftJson == null || !draftJson.contains("\"type\"")) {
            return draftJson;
        }
        try {
            Draft d = objectMapper.readValue(draftJson, Draft.class);
            if (d.getType() == null) {
                return draftJson;
            }
            if (!"shipment".equals(d.getType()) && !"receipt".equals(d.getType())) {
                return summarizeGeneric(draftJson);
            }
            boolean shipment = "shipment".equals(d.getType());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("草稿类型", shipment ? "出库单" : "入库单");
            m.put("模式", "edit".equals(d.getMode()) ? "修改已有单据 " + d.getOrderNo() : "新建");
            if (d.getSourceOrderNo() != null) {
                m.put("复制自", d.getSourceOrderNo());
            }
            m.put(shipment ? "客户" : "供应商", d.getMerchantName());
            m.put("仓库", d.getWarehouseName());
            m.put("单据类型", shipment ? ToolSupport.shipmentType(parseLong(d.getOptType()))
                : ToolSupport.receiptType(parseLong(d.getOptType())));
            m.put("日期", d.getBizDate());
            m.put("单价取法", priceModeLabel(d.getPriceMode(), shipment));
            m.put("备注", d.getRemark());
            List<Map<String, Object>> lines = new ArrayList<>();
            int max = 80;
            for (DraftLine l : d.getDetails()) {
                if (lines.size() >= max) {
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                if (l.getId() != null) {
                    row.put("明细id", l.getId());
                }
                row.put("skuId", l.getSkuId());
                row.put("商品", l.getItem() != null ? l.getItem().getItemName() : l.getItemName());
                row.put("规格", l.getItemSku() == null ? null : l.getItemSku().getSkuName());
                row.put("单位", l.getItem() == null ? null : l.getItem().getUnit());
                row.put("数量", plain(l.getQuantity()));
                row.put("单价", l.getPrice());
                row.put("金额", l.getAmount());
                if (l.getRemark() != null) {
                    row.put("备注", l.getRemark());
                }
                lines.add(row);
            }
            m.put("明细行数", d.getDetails().size());
            m.put("明细", lines);
            if (d.getDetails().size() > max) {
                m.put("明细说明", "只列出前 " + max + " 行，其余 " + (d.getDetails().size() - max) + " 行已在草稿中");
            }
            m.put("合计数量", d.getTotalQuantity());
            m.put("合计金额", d.getTotalAmount());
            if (!d.getUnresolved().isEmpty()) {
                m.put("unresolved", d.getUnresolved());
            }
            if (!d.getWarnings().isEmpty()) {
                m.put("warnings", d.getWarnings());
            }
            m.put("说明", "草稿已生成并会在前端表单中预填，用户核对后才会保存；不要再重复调用建单工具，除非用户要求调整。");
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            log.warn("草稿摘要失败: {}", e.getMessage());
            return draftJson;
        }
    }

    /**
     * 其它类型草稿（移库/盘点/动作）的通用精简：明细里的整块 item/itemSku 换成名字，其余字段原样保留。
     */
    @SuppressWarnings("unchecked")
    public String summarizeGeneric(String json) {
        if (json == null || !json.contains("\"type\"")) {
            return json;
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
            });
            Object type = m.get("type");
            if ("shipment".equals(type) || "receipt".equals(type)) {
                return summarize(json);
            }
            Object details = m.get("details");
            if (details instanceof List<?> list) {
                List<Map<String, Object>> compact = new ArrayList<>();
                int max = 80;
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> d) || compact.size() >= max) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    if (d.get("item") instanceof Map<?, ?> item) {
                        row.put("商品", item.get("itemName"));
                        row.put("单位", item.get("unit"));
                    }
                    if (d.get("itemSku") instanceof Map<?, ?> sku) {
                        row.put("规格", sku.get("skuName"));
                    }
                    for (Map.Entry<?, ?> e : d.entrySet()) {
                        String k = String.valueOf(e.getKey());
                        if ("item".equals(k) || "itemSku".equals(k) || "location".equals(k)) {
                            continue;
                        }
                        row.put(k, e.getValue());
                    }
                    compact.add(row);
                }
                m.put("details", compact);
                if (list.size() > max) {
                    m.put("明细说明", "只列出前 " + max + " 行，共 " + list.size() + " 行");
                }
            }
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            log.warn("草稿通用摘要失败: {}", e.getMessage());
            return json;
        }
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 给 GetOrderTool 用：一张单的可读描述 */
    public Map<String, Object> describe(SourceOrder s, boolean withLines) {
        boolean shipment = s.isShipment();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单据类型", shipment ? "出库单" : "入库单");
        m.put("单号", s.getOrderNo());
        m.put("状态", shipment ? ToolSupport.shipmentStatus(s.getStatus()) : ToolSupport.receiptStatus(s.getStatus()));
        m.put("业务类型", shipment ? ToolSupport.shipmentType(s.getOptType()) : ToolSupport.receiptType(s.getOptType()));
        m.put(shipment ? "客户" : "供应商", s.getMerchantName());
        m.put("仓库", s.getWarehouseName());
        m.put("日期", fmt(s.getBizDate() != null ? s.getBizDate() : (s.getCreateTime() == null ? null : s.getCreateTime().toLocalDate())));
        m.put("业务单号", s.getBizOrderNo());
        m.put("备注", s.getRemark());
        if (shipment && Boolean.TRUE.equals(s.getRecordOnly())) {
            m.put("纯记录单", true);
        }
        m.put("明细行数", s.getLines().size());
        m.put("总数量", plain(s.getTotalQuantity()));
        m.put("总金额", s.getTotalAmount());
        m.put("明细可修改", s.isEditable());
        m.put("修改说明", s.editableReason());
        m.put("orderId", s.getId());
        if (withLines) {
            List<Map<String, Object>> lines = new ArrayList<>();
            int i = 1;
            for (SourceLine l : s.getLines()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("行", i++);
                row.put("明细id", l.getId());
                row.put("skuId", l.getSkuId());
                row.put("商品", l.getItem() != null ? l.getItem().getItemName() : l.getItemName());
                row.put("规格", l.getItemSku() == null ? null : l.getItemSku().getSkuName());
                row.put("单位", l.getItem() == null ? null : l.getItem().getUnit());
                row.put("数量", plain(l.getQuantity()));
                row.put("单价", l.getPrice());
                row.put("金额", l.getAmount());
                if (l.getItemSku() != null) {
                    row.put("当前售价", l.getItemSku().getSellingPrice());
                    row.put("当前进价", l.getItemSku().getCostPrice());
                }
                if (l.getRemark() != null) {
                    row.put("备注", l.getRemark());
                }
                lines.add(row);
            }
            m.put("明细", lines);
        }
        return m;
    }
}
