package com.ruoyi.wms.utils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.entity.BaseOrderDetail;
import com.ruoyi.wms.domain.entity.Merchant;
import com.ruoyi.wms.domain.entity.Warehouse;
import com.ruoyi.wms.mapper.CheckOrderDetailMapper;
import com.ruoyi.wms.mapper.ItemSkuMapper;
import com.ruoyi.wms.mapper.MerchantMapper;
import com.ruoyi.wms.mapper.MovementOrderDetailMapper;
import com.ruoyi.wms.mapper.ReceiptOrderDetailMapper;
import com.ruoyi.wms.mapper.ShipmentOrderDetailMapper;
import com.ruoyi.wms.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 单据综合搜索辅助：把关键字解析成关联表（往来单位、仓库、商品明细）的主键集合，
 * 用于在单据列表的 {@code LambdaQueryWrapper} 中以 {@code IN} 方式参与“或”匹配，
 * 从而支持按客户/供应商名称、仓库名称、明细商品/规格等关联字段进行综合搜索。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class OrderKeywordSearcher {

    private final MerchantMapper merchantMapper;
    private final WarehouseMapper warehouseMapper;
    private final ItemSkuMapper itemSkuMapper;
    private final ReceiptOrderDetailMapper receiptOrderDetailMapper;
    private final ShipmentOrderDetailMapper shipmentOrderDetailMapper;
    private final CheckOrderDetailMapper checkOrderDetailMapper;
    private final MovementOrderDetailMapper movementOrderDetailMapper;

    /**
     * 关键字匹配的往来单位（客户/供应商）ID：按名称或编号模糊匹配。
     *
     * @param word 单个关键字
     * @return 匹配到的往来单位 ID 列表，无匹配时返回空列表（不会为 null）
     */
    public List<Long> matchMerchantIds(String word) {
        if (StringUtils.isBlank(word)) {
            return Collections.emptyList();
        }
        return merchantMapper.selectList(Wrappers.<Merchant>lambdaQuery()
                .select(Merchant::getId)
                .like(Merchant::getMerchantName, word)
                .or().like(Merchant::getMerchantCode, word))
            .stream().map(Merchant::getId).collect(Collectors.toList());
    }

    /**
     * 关键字匹配的仓库 ID：按名称或编号模糊匹配。
     *
     * @param word 单个关键字
     * @return 匹配到的仓库 ID 列表，无匹配时返回空列表（不会为 null）
     */
    public List<Long> matchWarehouseIds(String word) {
        if (StringUtils.isBlank(word)) {
            return Collections.emptyList();
        }
        return warehouseMapper.selectList(Wrappers.<Warehouse>lambdaQuery()
                .select(Warehouse::getId)
                .like(Warehouse::getWarehouseName, word)
                .or().like(Warehouse::getWarehouseCode, word))
            .stream().map(Warehouse::getId).collect(Collectors.toList());
    }

    /** 关键字匹配的规格(sku) ID：按规格名称/编号/条码/商品名称/编号模糊匹配。 */
    private List<Long> matchSkuIds(String word) {
        if (StringUtils.isBlank(word)) {
            return Collections.emptyList();
        }
        return KeywordUtils.expandWord(word).stream()
            .flatMap(alias -> itemSkuMapper.selectIdsByKeyword(alias).stream())
            .distinct()
            .collect(Collectors.toList());
    }

    /** 含某关键字命中商品的入库单 ID（通过入库单明细反查）。 */
    public List<Long> matchReceiptOrderIds(String word) {
        return orderIdsByDetail(receiptOrderDetailMapper, matchSkuIds(word));
    }

    /** 含某关键字命中商品的出库单 ID（通过出库单明细反查）。 */
    public List<Long> matchShipmentOrderIds(String word) {
        return orderIdsByDetail(shipmentOrderDetailMapper, matchSkuIds(word));
    }

    /** 含某关键字命中商品的盘库单 ID（通过盘库单明细反查）。 */
    public List<Long> matchCheckOrderIds(String word) {
        return orderIdsByDetail(checkOrderDetailMapper, matchSkuIds(word));
    }

    /** 含某关键字命中商品的移库单 ID（通过移库单明细反查）。 */
    public List<Long> matchMovementOrderIds(String word) {
        return orderIdsByDetail(movementOrderDetailMapper, matchSkuIds(word));
    }

    /**
     * 根据命中的规格 ID，在指定单据明细表中反查所属单据 ID（去重）。
     *
     * @param detailMapper 单据明细 mapper（明细均继承自 {@link BaseOrderDetail}）
     * @param skuIds       命中的规格 ID
     */
    private <T extends BaseOrderDetail> List<Long> orderIdsByDetail(BaseMapperPlus<T, ?> detailMapper, List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return detailMapper.selectList(Wrappers.<T>lambdaQuery()
                .select(BaseOrderDetail::getOrderId)
                .in(BaseOrderDetail::getSkuId, skuIds))
            .stream()
            .map(BaseOrderDetail::getOrderId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }
}
