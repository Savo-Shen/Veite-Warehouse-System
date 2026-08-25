package com.ruoyi.wms.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.wms.domain.bo.ShipmentOrderBo;
import com.ruoyi.wms.domain.bo.ShipmentOrderDetailBo;
import com.ruoyi.wms.domain.entity.ShipmentOrderLog;
import com.ruoyi.wms.domain.vo.ShipmentOrderDetailVo;
import com.ruoyi.wms.domain.vo.ShipmentOrderLogVo;
import com.ruoyi.wms.domain.vo.ShipmentOrderVo;
import com.ruoyi.wms.mapper.ShipmentOrderLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 出库单变更历史
 *
 * 单据表上的 update_by / update_time 只留得住「最后一次是谁改的」。纯记录单是事后备查用的，
 * 改动比正常出库单频繁（价格记错了、客户又加了一项），回头查的时候真正想知道的是
 * 「改了什么、之前是多少」，所以这里把每次改动的差异算成一句人话存下来。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ShipmentOrderLogService {

    private final ShipmentOrderLogMapper shipmentOrderLogMapper;
    private final MerchantService merchantService;

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_SHIPMENT = "SHIPMENT";
    public static final String ACTION_VOID = "VOID";

    /** 摘要列宽 2000，留点余量截断，别让一次批量改动把插入撑失败 */
    private static final int SUMMARY_MAX = 1900;

    /**
     * 查一张单的全部历史，新的在前
     */
    public List<ShipmentOrderLogVo> queryByOrderId(Long orderId) {
        LambdaQueryWrapper<ShipmentOrderLog> lqw = Wrappers.lambdaQuery();
        lqw.eq(ShipmentOrderLog::getOrderId, orderId);
        lqw.orderByDesc(ShipmentOrderLog::getCreateTime).orderByDesc(ShipmentOrderLog::getId);
        return shipmentOrderLogMapper.selectVoList(lqw);
    }

    /**
     * 记一条历史。写历史失败不能把业务操作带崩——单据本身已经存好了，
     * 少一条审计记录比整单回滚代价小得多，所以这里吞掉异常只打日志。
     */
    public void record(Long orderId, String orderNo, String action, String summary) {
        try {
            ShipmentOrderLog entity = new ShipmentOrderLog();
            entity.setOrderId(orderId);
            entity.setOrderNo(orderNo);
            entity.setAction(action);
            entity.setSummary(StringUtils.isBlank(summary) ? null : truncate(summary));
            entity.setCreateBy(com.ruoyi.common.satoken.utils.LoginHelper.getUsername());
            shipmentOrderLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写出库单变更历史失败，orderId={}, action={}", orderId, action, e);
        }
    }

    /**
     * 比较修改前后，拼出变更摘要。没有任何实质变化时返回 null，由调用方决定不记这一条，
     * 免得点开编辑页什么都没动、点一下保存也刷出一条历史。
     */
    public String diff(ShipmentOrderVo before, ShipmentOrderBo after) {
        if (before == null) {
            return null;
        }
        List<String> changes = new ArrayList<>();
        diffHeader(before, after, changes);
        diffDetails(before.getDetails(), after.getDetails(), changes);
        return changes.isEmpty() ? null : String.join("；", changes);
    }

    private void diffHeader(ShipmentOrderVo before, ShipmentOrderBo after, List<String> changes) {
        addIfChanged(changes, "客户", merchantName(before.getMerchantId()), merchantName(after.getMerchantId()));
        addIfChanged(changes, "业务单号", before.getBizOrderNo(), after.getBizOrderNo());
        addIfChanged(changes, "出库日期", text(before.getBizDate()), text(after.getBizDate()));
        addIfChanged(changes, "备注", before.getRemark(), after.getRemark());
        addIfChanged(changes, "总数量", number(before.getTotalQuantity()), number(after.getTotalQuantity()));
        addIfChanged(changes, "总金额", number(before.getTotalAmount()), number(after.getTotalAmount()));
    }

    /**
     * 明细按「行标识」配对：纯记录单没有 sku，只能拿手输的商品名当标识；
     * 正常出库单用 skuId。同名的行改了价格算「改动」，名字变了算一删一增——
     * 这对纯记录单是对的：把商品名改掉基本就等于换了一样东西。
     */
    private void diffDetails(List<ShipmentOrderDetailVo> before, List<ShipmentOrderDetailBo> after,
                             List<String> changes) {
        Map<String, ShipmentOrderDetailVo> beforeMap = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(before)) {
            before.forEach(it -> beforeMap.put(detailKey(it.getSkuId(), it.getItemName()), it));
        }
        Map<String, ShipmentOrderDetailBo> afterMap = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(after)) {
            after.forEach(it -> afterMap.put(detailKey(it.getSkuId(), it.getItemName()), it));
        }

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(beforeMap.keySet());
        keys.addAll(afterMap.keySet());

        for (String key : keys) {
            ShipmentOrderDetailVo b = beforeMap.get(key);
            ShipmentOrderDetailBo a = afterMap.get(key);
            String label = displayName(b == null ? a.getItemName() : b.getItemName(), key);
            if (b == null) {
                changes.add("新增明细「" + label + "」" + lineBrief(a.getQuantity(), a.getSalePrice()));
                continue;
            }
            if (a == null) {
                changes.add("删除明细「" + label + "」" + lineBrief(b.getQuantity(), b.getSalePrice()));
                continue;
            }
            List<String> lineChanges = new ArrayList<>();
            addIfChanged(lineChanges, "数量", number(b.getQuantity()), number(a.getQuantity()));
            addIfChanged(lineChanges, "成本价", number(b.getCostPrice()), number(a.getCostPrice()));
            addIfChanged(lineChanges, "销售价", number(b.getSalePrice()), number(a.getSalePrice()));
            addIfChanged(lineChanges, "备注", b.getRemark(), a.getRemark());
            if (!lineChanges.isEmpty()) {
                changes.add("「" + label + "」" + String.join("、", lineChanges));
            }
        }
    }

    /**
     * 明细行标识：有 sku 用 sku，没有（纯记录单）用商品名
     */
    private String detailKey(Long skuId, String itemName) {
        if (skuId != null) {
            return "sku:" + skuId;
        }
        return "name:" + StringUtils.trimToEmpty(itemName);
    }

    private String displayName(String itemName, String key) {
        if (StringUtils.isNotBlank(itemName)) {
            return itemName;
        }
        // 正常出库单的明细不带商品名，退回用 sku 标识，至少能看出是哪一行
        return key.startsWith("sku:") ? "规格 " + key.substring(4) : "未命名";
    }

    private String lineBrief(BigDecimal quantity, BigDecimal salePrice) {
        String qty = quantity == null ? "1" : number(quantity);
        String price = salePrice == null ? "未填价" : "@" + number(salePrice);
        return "（×" + qty + " " + price + "）";
    }

    private void addIfChanged(List<String> changes, String label, String before, String after) {
        String b = StringUtils.trimToEmpty(before);
        String a = StringUtils.trimToEmpty(after);
        if (Objects.equals(b, a)) {
            return;
        }
        changes.add(label + " " + blankAsPlaceholder(b) + " → " + blankAsPlaceholder(a));
    }

    private String blankAsPlaceholder(String value) {
        return StringUtils.isBlank(value) ? "（空）" : value;
    }

    /**
     * BigDecimal 直接 toString 会把 2.00 和 2 当成两个值，用 stripTrailingZeros 归一，
     * 否则前端传 2 而库里存 2.00 时会刷出一条假的「数量 2.00 → 2」。
     */
    private String number(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String merchantName(Long merchantId) {
        if (merchantId == null) {
            return "";
        }
        try {
            var vo = merchantService.queryById(merchantId);
            return vo == null ? String.valueOf(merchantId) : vo.getMerchantName();
        } catch (Exception e) {
            // 客户被删了之类的，退回显示 id，不能因为查不到名字就不记这条历史
            return String.valueOf(merchantId);
        }
    }

    private String truncate(String summary) {
        return summary.length() <= SUMMARY_MAX ? summary : summary.substring(0, SUMMARY_MAX) + "…（已截断）";
    }
}
