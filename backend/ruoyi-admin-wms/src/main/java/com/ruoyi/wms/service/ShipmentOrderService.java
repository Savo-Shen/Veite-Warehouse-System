package com.ruoyi.wms.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.constant.HttpStatus;
import com.ruoyi.common.core.constant.ServiceConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.exception.base.BaseException;
import com.ruoyi.common.core.service.ConfigService;
import com.ruoyi.common.core.utils.MapstructUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.ShipmentOrderBo;
import com.ruoyi.wms.domain.bo.ShipmentOrderDetailBo;
import com.ruoyi.wms.domain.bo.ShipmentOrderSupplementBo;
import com.ruoyi.wms.domain.entity.ShipmentOrder;
import com.ruoyi.wms.domain.entity.ShipmentOrderDetail;
import com.ruoyi.wms.domain.vo.ShipmentOrderVo;
import com.ruoyi.wms.mapper.ShipmentOrderMapper;
import com.ruoyi.wms.utils.KeywordUtils;
import com.ruoyi.wms.utils.OrderKeywordSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 出库单Service业务层处理
 *
 * @author zcc
 * @date 2024-08-01
 */
@RequiredArgsConstructor
@Service
public class ShipmentOrderService {

    private final ShipmentOrderMapper shipmentOrderMapper;
    private final ShipmentOrderDetailService shipmentOrderDetailService;
    private final InventoryService inventoryService;
    private final InventoryHistoryService inventoryHistoryService;
    private final OrderKeywordSearcher orderKeywordSearcher;
    private final ConfigService configService;
    private final ShipmentOrderLogService shipmentOrderLogService;

    /**
     * 是否允许出库扣成负库存的系统开关（基础资料 → 环境配置）
     */
    private static final String ALLOW_NEGATIVE_KEY = "wms.inventory.allowNegative";

    /**
     * 查询出库单
     */
    public ShipmentOrderVo queryById(Long id){
        ShipmentOrderVo shipmentOrderVo = shipmentOrderMapper.selectVoById(id);
        if (shipmentOrderVo == null) {
            throw new BaseException("出库单不存在");
        }
        shipmentOrderVo.setDetails(shipmentOrderDetailService.queryByShipmentOrderId(shipmentOrderVo.getId()));
        return shipmentOrderVo;
    }

    /**
     * 查询出库单列表
     */
    public TableDataInfo<ShipmentOrderVo> queryPageList(ShipmentOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ShipmentOrder> lqw = buildQueryWrapper(bo);
        Page<ShipmentOrderVo> result = shipmentOrderMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询出库单列表
     */
    public List<ShipmentOrderVo> queryList(ShipmentOrderBo bo) {
        LambdaQueryWrapper<ShipmentOrder> lqw = buildQueryWrapper(bo);
        return shipmentOrderMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<ShipmentOrder> buildQueryWrapper(ShipmentOrderBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<ShipmentOrder> lqw = Wrappers.lambdaQuery();
        // 综合搜索：按空白拆词，多词之间为“与”，每个词在单号/业务单号/备注/客户/仓库/明细商品间为“或”
        for (String word : KeywordUtils.splitWords(bo.getKeyword())) {
            List<Long> merchantIds = orderKeywordSearcher.matchMerchantIds(word);
            List<Long> warehouseIds = orderKeywordSearcher.matchWarehouseIds(word);
            List<Long> detailOrderIds = orderKeywordSearcher.matchShipmentOrderIds(word);
            lqw.and(wrapper -> {
                wrapper.like(ShipmentOrder::getOrderNo, word)
                    .or().like(ShipmentOrder::getBizOrderNo, word)
                    .or().like(ShipmentOrder::getRemark, word);
                if (!merchantIds.isEmpty()) {
                    wrapper.or().in(ShipmentOrder::getMerchantId, merchantIds);
                }
                if (!warehouseIds.isEmpty()) {
                    wrapper.or().in(ShipmentOrder::getWarehouseId, warehouseIds);
                }
                if (!detailOrderIds.isEmpty()) {
                    wrapper.or().in(ShipmentOrder::getId, detailOrderIds);
                }
            });
        }
        lqw.like(StringUtils.isNotBlank(bo.getOrderNo()), ShipmentOrder::getOrderNo, bo.getOrderNo());
        lqw.like(StringUtils.isNotBlank(bo.getBizOrderNo()), ShipmentOrder::getBizOrderNo, bo.getBizOrderNo());
        lqw.eq(bo.getOptType() != null, ShipmentOrder::getOptType, bo.getOptType());
        lqw.eq(bo.getRecordOnly() != null, ShipmentOrder::getRecordOnly, bo.getRecordOnly());
        lqw.eq(bo.getMerchantId() != null, ShipmentOrder::getMerchantId, bo.getMerchantId());
        lqw.eq(bo.getWarehouseId() != null, ShipmentOrder::getWarehouseId, bo.getWarehouseId());
        lqw.eq(bo.getTotalAmount() != null, ShipmentOrder::getTotalAmount, bo.getTotalAmount());
        lqw.eq(bo.getTotalQuantity() != null, ShipmentOrder::getTotalQuantity, bo.getTotalQuantity());
        lqw.eq(bo.getOrderStatus() != null, ShipmentOrder::getOrderStatus, bo.getOrderStatus());
        lqw.like(StringUtils.isNotBlank(bo.getRemark()), ShipmentOrder::getRemark, bo.getRemark());
        lqw.between(params.get("beginTime") != null && params.get("endTime") != null,
            ShipmentOrder::getBizDate, params.get("beginTime"), params.get("endTime"));
        // 按业务日期倒序，同一天内再按录入时间，补录的单子会落回它该在的位置
        lqw.orderByDesc(ShipmentOrder::getBizDate);
        lqw.orderByDesc(ShipmentOrder::getCreateTime);
        return lqw;
    }

    /**
     * 暂存出库单
     */
    @Transactional
    public void insertByBo(ShipmentOrderBo bo) {
        // 校验出库单号唯一性
        validateShipmentOrderNo(bo.getOrderNo());
        fillOrderTotals(bo);
        fillBizDate(bo);
        // 创建出库单
        ShipmentOrder add = MapstructUtils.convert(bo, ShipmentOrder.class);
        shipmentOrderMapper.insert(add);
        bo.setId(add.getId());
        List<ShipmentOrderDetailBo> detailBoList = bo.getDetails();
        List<ShipmentOrderDetail> addDetailList = MapstructUtils.convert(detailBoList, ShipmentOrderDetail.class);
        addDetailList.forEach(it -> it.setOrderId(add.getId()));
        shipmentOrderDetailService.saveDetails(addDetailList);
        shipmentOrderLogService.record(add.getId(), add.getOrderNo(), ShipmentOrderLogService.ACTION_CREATE,
            buildCreateSummary(bo));
    }

    public void validateShipmentOrderNo(String shipmentOrderNo) {
        LambdaQueryWrapper<ShipmentOrder> receiptOrderLqw = Wrappers.lambdaQuery();
        receiptOrderLqw.eq(ShipmentOrder::getOrderNo, shipmentOrderNo);
        ShipmentOrder shipmentOrder = shipmentOrderMapper.selectOne(receiptOrderLqw);
        Assert.isNull(shipmentOrder, "出库单号重复，请手动修改");
    }


    /**
     * 修改出库单
     */
    @Transactional
    public void updateByBo(ShipmentOrderBo bo) {
        validateRecordOnlyUnchanged(bo);
        fillOrderTotals(bo);
        fillBizDate(bo);
        // 差异要在落库前算，否则查出来的已经是新值了
        ShipmentOrderVo before = queryById(bo.getId());
        String summary = shipmentOrderLogService.diff(before, bo);
        // 更新出库单
        ShipmentOrder update = MapstructUtils.convert(bo, ShipmentOrder.class);
        shipmentOrderMapper.updateById(update);
        // 保存出库单明细
        List<ShipmentOrderDetail> detailList = MapstructUtils.convert(bo.getDetails(), ShipmentOrderDetail.class);
        detailList.forEach(it -> it.setOrderId(bo.getId()));
        shipmentOrderDetailService.saveDetails(detailList);
        if (isRecordOnly(bo)) {
            // 纯记录单以本次提交为准，提交里没有的行直接删掉（正常出库单维持原样，
            // 由前端的删除明细接口负责，这里不动它的语义）
            List<Long> keepIds = detailList.stream()
                .map(ShipmentOrderDetail::getId)
                .filter(Objects::nonNull)
                .toList();
            shipmentOrderDetailService.removeDetailsNotIn(bo.getId(), keepIds);
        }
        // 作废走的也是这个方法，单独标出来；什么都没改就不记，免得点开保存一次就多一条空历史
        if (ServiceConstants.ShipmentOrderStatus.INVALID.equals(bo.getOrderStatus())
            && !ServiceConstants.ShipmentOrderStatus.INVALID.equals(before.getOrderStatus())) {
            shipmentOrderLogService.record(bo.getId(), bo.getOrderNo(), ShipmentOrderLogService.ACTION_VOID,
                summary == null ? "作废" : "作废（同时改动：" + summary + "）");
        } else if (summary != null) {
            shipmentOrderLogService.record(bo.getId(), bo.getOrderNo(), ShipmentOrderLogService.ACTION_UPDATE, summary);
        }
    }

    /**
     * 事后补充备注和现场照片。
     *
     * 出库单出库/作废之后整单锁死，是怕改了明细跟已经动过的库存对不上账；备注和照片
     * 既不进库存也不进金额，签收单第二天才拍到、当时漏写一句说明都是常事，得能补进去。
     * 所以这里不看单据状态，但只认这两个字段，其余一概不接——想改明细还是得走正常修改。
     */
    @Transactional
    public void supplement(ShipmentOrderSupplementBo bo) {
        ShipmentOrder exist = shipmentOrderMapper.selectById(bo.getId());
        if (exist == null) {
            throw new BaseException("出库单不存在");
        }
        String summary = shipmentOrderLogService.diffSupplement(exist, bo);
        if (summary == null) {
            // 什么都没改就当没点过，免得刷出一条空历史
            return;
        }
        // 清空备注要写 null 进库，updateById 的非空策略会把 null 当「不更新」跳过，
        // 所以这里显式 set 两个字段
        LambdaUpdateWrapper<ShipmentOrder> luw = Wrappers.lambdaUpdate();
        luw.eq(ShipmentOrder::getId, bo.getId());
        luw.set(ShipmentOrder::getRemark, StringUtils.isBlank(bo.getRemark()) ? null : bo.getRemark().trim());
        luw.set(ShipmentOrder::getSupplementImageIds,
            StringUtils.isBlank(bo.getSupplementImageIds()) ? null : bo.getSupplementImageIds().trim());
        // 传一个空实体进去，让 MP 的自动填充把 update_by / update_time 带上——
        // 列表页的「更新」两列否则还停在建单那天，看着像谁都没动过
        shipmentOrderMapper.update(new ShipmentOrder(), luw);
        shipmentOrderLogService.record(bo.getId(), exist.getOrderNo(),
            ShipmentOrderLogService.ACTION_SUPPLEMENT, summary);
    }

    /**
     * 批量删除出库单
     */
    public void deleteById(Long id) {
        validateIdBeforeDelete(id);
        shipmentOrderMapper.deleteById(id);
    }

    public void validateIdBeforeDelete(Long id) {
        ShipmentOrderVo shipmentOrderVo = queryById(id);
        if (shipmentOrderVo == null) {
            throw new BaseException("出库单不存在");
        }
        if (ServiceConstants.ShipmentOrderStatus.FINISH.equals(shipmentOrderVo.getOrderStatus())) {
            throw new ServiceException("删除失败", HttpStatus.CONFLICT,"出库单【" + shipmentOrderVo.getOrderNo() + "】已出库，无法删除！");
        }
    }

    /**
     * 出库
     * @param bo
     */
    @Transactional
    public void shipment(ShipmentOrderBo bo) {
        // 1.校验商品明细不能为空！
        validateBeforeShipment(bo);
        // 2. 保存入库单和入库单明细
        boolean isNew = Objects.isNull(bo.getId());
        // 状态要在保存前读，保存完就分不出这次是不是真的发生了「出库」这个动作了
        Integer beforeStatus = isNew ? null : statusOf(bo.getId());
        if (isNew) {
            insertByBo(bo);
        } else {
            updateByBo(bo);
        }
        // 只有「本来是暂存、这次才真出库」才单记一条：新建时直接出库的，
        // CREATE 那条已经写明了，再记一条 SHIPMENT 就是同一个动作记两遍。
        if (!isNew && !ServiceConstants.ShipmentOrderStatus.FINISH.equals(beforeStatus)) {
            shipmentOrderLogService.record(bo.getId(), bo.getOrderNo(), ShipmentOrderLogService.ACTION_SHIPMENT,
                isRecordOnly(bo) ? "由暂存转为正式记录（不影响库存）" : "完成出库");
        }
        // 3.纯记录单到此为止：它的明细不挂 SKU，只是把这笔交易的价格留个底，
        //   既不能扣库存，也不该混进库存流水（流水的前后数对它没有意义）。
        if (isRecordOnly(bo)) {
            return;
        }
        // 4.更新库存：Inventory表
        // 负库存要「系统开关打开」且「本次提交显式确认过」才放行，两者缺一不可
        boolean allowNegative = isNegativeAllowed() && Boolean.TRUE.equals(bo.getAllowNegative());
        inventoryService.subtract(bo.getDetails(), allowNegative);

        // 5.创建库存记录
        inventoryHistoryService.saveInventoryHistory(bo,ServiceConstants.InventoryHistoryOrderType.SHIPMENT,false);
    }

    /**
     * 单据用途一旦落库就不能再改。正常单改成纯记录单，已经扣掉的库存不会退回来；
     * 反过来纯记录单改成正常单，明细没有 SKU 也扣不了库存。两个方向都会把账做坏。
     */
    private void validateRecordOnlyUnchanged(ShipmentOrderBo bo) {
        if (bo.getId() == null) {
            return;
        }
        ShipmentOrder exist = shipmentOrderMapper.selectById(bo.getId());
        if (exist == null) {
            return;
        }
        if (Boolean.TRUE.equals(exist.getRecordOnly()) != isRecordOnly(bo)) {
            throw new BaseException("单据用途已保存，不能在「正常出库」和「纯记录」之间切换，请新建一张单");
        }
    }

    /**
     * 只取状态，不带明细，比 queryById 轻
     */
    private Integer statusOf(Long id) {
        ShipmentOrder exist = shipmentOrderMapper.selectById(id);
        return exist == null ? null : exist.getOrderStatus();
    }

    private String buildCreateSummary(ShipmentOrderBo bo) {
        boolean finished = ServiceConstants.ShipmentOrderStatus.FINISH.equals(bo.getOrderStatus());
        if (isRecordOnly(bo)) {
            return finished ? "新建纯记录单" : "暂存纯记录单";
        }
        return finished ? "新建出库单并完成出库" : "暂存出库单";
    }

    /**
     * 是否纯记录单。老单据这一列为 null，按正常出库单处理
     */
    private boolean isRecordOnly(ShipmentOrderBo bo) {
        return Boolean.TRUE.equals(bo.getRecordOnly());
    }


    /**
     * 系统是否允许出库扣成负库存
     */
    public boolean isNegativeAllowed() {
        return Boolean.parseBoolean(configService.getConfigValue(ALLOW_NEGATIVE_KEY));
    }

    /**
     * 没选业务日期就按今天算。老单据（biz_date 为空的）已经在迁移脚本里回填成 create_time 那天。
     */
    private void fillBizDate(ShipmentOrderBo bo) {
        if (bo.getBizDate() == null) {
            bo.setBizDate(LocalDate.now());
        }
    }

    private void validateBeforeShipment(ShipmentOrderBo bo) {
        if (CollUtil.isEmpty(bo.getDetails())) {
            throw new BaseException("商品明细不能为空！");
        }
        if (!isRecordOnly(bo)) {
            return;
        }
        // 纯记录单不挂 SKU，商品名是这行唯一的标识，空了这条记录就没法回看
        boolean hasBlankName = bo.getDetails().stream()
            .anyMatch(it -> StringUtils.isBlank(it.getItemName()));
        if (hasBlankName) {
            throw new BaseException("纯记录单的商品名称不能为空！");
        }
        // 数量可以不填（按一件算），但填了就不能是 0 或负数，否则金额算出来没有意义
        boolean hasBadQuantity = bo.getDetails().stream()
            .anyMatch(it -> it.getQuantity() != null && it.getQuantity().signum() <= 0);
        if (hasBadQuantity) {
            throw new BaseException("纯记录单的数量必须大于 0！");
        }
    }

    private void fillOrderTotals(ShipmentOrderBo bo) {
        if (isRecordOnly(bo)) {
            fillRecordOnlyTotals(bo);
            return;
        }
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean hasAmount = false;

        if (CollUtil.isNotEmpty(bo.getDetails())) {
            for (ShipmentOrderDetailBo detail : bo.getDetails()) {
                if (detail.getQuantity() != null) {
                    totalQuantity = totalQuantity.add(detail.getQuantity());
                }
                if (detail.getAmount() != null) {
                    totalAmount = totalAmount.add(detail.getAmount());
                    hasAmount = true;
                }
            }
        }

        bo.setTotalQuantity(totalQuantity);
        bo.setTotalAmount(hasAmount ? totalAmount : BigDecimal.ZERO);
    }

    /**
     * 纯记录单的合计：金额 = 数量 × 销售价，总额是各行金额之和。
     * 这里的数量只用来算钱和留档，不会写进库存——纯记录单从头到尾不碰 wms_inventory。
     * 没填数量的按一件算（老单据的 quantity 为空，回看时也当一件）。
     */
    private void fillRecordOnlyTotals(ShipmentOrderBo bo) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (CollUtil.isNotEmpty(bo.getDetails())) {
            for (ShipmentOrderDetailBo detail : bo.getDetails()) {
                BigDecimal quantity = detail.getQuantity() == null ? BigDecimal.ONE : detail.getQuantity();
                detail.setQuantity(quantity);
                BigDecimal amount = detail.getSalePrice() == null
                    ? null
                    : detail.getSalePrice().multiply(quantity);
                detail.setAmount(amount);
                totalQuantity = totalQuantity.add(quantity);
                if (amount != null) {
                    totalAmount = totalAmount.add(amount);
                }
            }
        }
        bo.setTotalQuantity(totalQuantity);
        bo.setTotalAmount(totalAmount);
    }
}
