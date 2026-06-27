package com.ruoyi.wms.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.constant.HttpStatus;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.MapstructUtils;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.BaseOrderDetailBo;
import com.ruoyi.wms.domain.bo.CheckOrderDetailBo;
import com.ruoyi.wms.domain.bo.InventoryBo;
import com.ruoyi.wms.domain.entity.Inventory;
import com.ruoyi.wms.domain.entity.Warehouse;
import com.ruoyi.wms.domain.vo.InventoryExportVo;
import com.ruoyi.wms.domain.vo.InventoryVo;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.mapper.InventoryMapper;
import com.ruoyi.wms.mapper.WarehouseMapper;
import com.ruoyi.wms.utils.KeywordUtils;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存Service业务层处理
 *
 * @author zcc
 * @date 2024-07-19
 */
@RequiredArgsConstructor
@Service
public class InventoryService extends ServiceImpl<InventoryMapper, Inventory> {

    private final InventoryMapper inventoryMapper;
    private final ItemSkuService itemSkuService;
    // 注入 Mapper 而非 WarehouseService，避免与 WarehouseService 形成循环依赖
    private final WarehouseMapper warehouseMapper;

    /**
     * 查询库存
     */
    public InventoryVo queryById(Long id){
        return inventoryMapper.selectVoById(id);
    }

    /**
     * 查询库存列表
     */
    public List<InventoryVo> queryList(InventoryBo bo) {
        LambdaQueryWrapper<Inventory> lqw = buildQueryWrapper(bo);
        List<InventoryVo> vos = inventoryMapper.selectVoList(lqw);
        if(CollUtil.isNotEmpty(vos)){
            Set<Long> skuIds = vos.stream().map(InventoryVo::getSkuId).collect(Collectors.toSet());
            Map<Long, ItemSkuMapVo> itemSkuMap = itemSkuService.queryItemSkuMapVosByIds(skuIds);
            vos.forEach(it -> {
                ItemSkuMapVo itemSkuMapVo = itemSkuMap.get(it.getSkuId());
                if (itemSkuMapVo != null) {
                    it.setItemSku(itemSkuMapVo.getItemSku());
                    it.setItem(itemSkuMapVo.getItem());
                    it.setLocation(itemSkuMapVo.getLocation());
                } else {
                    // log.warn("未找到 skuId={} 对应的 ItemSkuMapVo", it.getSkuId());
                    it.setItemSku(null); // 或默认值
                    it.setItem(null);    // 或默认值
                    it.setLocation(null);
                }
            });
        }
        return vos;
    }

    private LambdaQueryWrapper<Inventory> buildQueryWrapper(InventoryBo bo) {
        LambdaQueryWrapper<Inventory> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(bo.getSkuId() != null, Inventory::getSkuId, bo.getSkuId());
        wrapper.eq(bo.getWarehouseId() != null, Inventory::getWarehouseId, bo.getWarehouseId());
        wrapper.eq(bo.getQuantity() != null, Inventory::getQuantity, bo.getQuantity());
        return wrapper;
    }

    /**
     * 新增库存
     */
    public void insertByBo(InventoryBo bo) {
        Inventory add = MapstructUtils.convert(bo, Inventory.class);
        inventoryMapper.insert(add);
    }

    /**
     * 修改库存
     */
    public void updateByBo(InventoryBo bo) {
        Inventory update = MapstructUtils.convert(bo, Inventory.class);
        inventoryMapper.updateById(update);
    }

    /**
     * 批量删除库存
     */
    public void deleteByIds(Collection<Long> ids) {
        inventoryMapper.deleteBatchIds(ids);
    }

    /**
     * 校验规格是否有库存
     * @param skuIds
     * @return
     */
    public boolean existsBySkuIds(@NotEmpty Collection<Long> skuIds) {
        LambdaQueryWrapper<Inventory> lqw = Wrappers.lambdaQuery();
        lqw.in(Inventory::getSkuId, skuIds);
        return inventoryMapper.exists(lqw);
    }

    public TableDataInfo<InventoryVo> queryWarehouseBoardList(InventoryBo bo, PageQuery pageQuery, String itemKeywords) {
            return TableDataInfo.build(inventoryMapper.queryWarehouseBoardList(pageQuery.build(), bo, KeywordUtils.splitWordGroups(itemKeywords)));
    }

    public TableDataInfo<InventoryVo> queryItemBoardList(InventoryBo bo, PageQuery pageQuery, String itemKeywords) {
        Page<InventoryVo> result = inventoryMapper.queryItemBoardList(pageQuery.build(), bo, KeywordUtils.splitWordGroups(itemKeywords));
        return TableDataInfo.build(result);
    }

    /**
     * 查询库存导出数据
     * <p>
     * 复用看板查询条件（支持智能搜索及高级搜索），当传入勾选的库存 id 时，
     * 仅导出勾选的记录；否则导出全部符合搜索条件的记录。
     *
     * @param bo           查询条件
     * @param itemKeywords 智能搜索关键字
     * @param ids          勾选的库存 id（可为空）
     */
    public List<InventoryExportVo> queryExportList(InventoryBo bo, String itemKeywords, Collection<Long> ids) {
        List<InventoryVo> list = inventoryMapper.queryExportList(bo, KeywordUtils.splitWordGroups(itemKeywords));
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 仅导出勾选的记录
        if (CollUtil.isNotEmpty(ids)) {
            Set<Long> idSet = new HashSet<>(ids);
            list = list.stream().filter(it -> idSet.contains(it.getId())).collect(Collectors.toList());
        }
        // 仓库名称映射
        Map<Long, String> warehouseNameMap = warehouseMapper.selectList(null).stream()
            .collect(Collectors.toMap(Warehouse::getId, Warehouse::getWarehouseName, (a, b) -> a));

        return list.stream().map(it -> {
            InventoryExportVo vo = new InventoryExportVo();
            if (it.getItem() != null) {
                vo.setItemName(it.getItem().getItemName());
                vo.setItemCode(it.getItem().getItemCode());
            }
            BigDecimal costPrice = null;
            BigDecimal sellingPrice = null;
            if (it.getItemSku() != null) {
                vo.setSkuName(it.getItemSku().getSkuName());
                vo.setSkuCode(it.getItemSku().getSkuCode());
                costPrice = it.getItemSku().getCostPrice();
                sellingPrice = it.getItemSku().getSellingPrice();
            }
            if (it.getLocation() != null) {
                String code = it.getLocation().getLocationCode();
                String name = it.getLocation().getLocationName();
                if (StrUtil.isNotBlank(code) && StrUtil.isNotBlank(name)) {
                    vo.setLocationName(code + "（" + name + "）");
                } else {
                    vo.setLocationName(StrUtil.isNotBlank(code) ? code : name);
                }
            }
            vo.setWarehouseName(warehouseNameMap.get(it.getWarehouseId()));
            BigDecimal quantity = it.getQuantity() == null ? BigDecimal.ZERO : it.getQuantity();
            vo.setQuantity(quantity);
            vo.setCostPrice(costPrice);
            vo.setSellingPrice(sellingPrice);
            if (costPrice != null) {
                vo.setTotalCost(quantity.multiply(costPrice));
            }
            if (sellingPrice != null) {
                vo.setTotalSelling(quantity.multiply(sellingPrice));
            }
            vo.setRemark(it.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    public void updateInventory(List<CheckOrderDetailBo> details) {
        List<Inventory> updateInventoryList=new LinkedList<>();
        List<Inventory> insertInventoryList=new LinkedList<>();

        details.forEach(detail -> {
            LambdaQueryWrapper<Inventory> wrapper = Wrappers.lambdaQuery();
            if(detail.getInventoryId()!=null){
                wrapper.eq(Inventory::getId,detail.getInventoryId());
                Inventory inventory = inventoryMapper.selectOne(wrapper);
                if(inventory.getQuantity().compareTo(detail.getQuantity())!=0){
                    ItemSkuMapVo itemSkuMapVo = itemSkuService.queryItemSkuMapVo(detail.getSkuId());
                    throw new ServiceException(
                        "账面库存不匹配："+itemSkuMapVo.getItem().getItemName()+"（"+itemSkuMapVo.getItemSku().getSkuName()+"）",
                        HttpStatus.CONFLICT,
                        "填写账面库存："+detail.getQuantity()+" 实际账面库存："+inventory.getQuantity());
                }else {
                    if(!inventory.getQuantity().equals(detail.getCheckQuantity())){
                        inventory.setQuantity(detail.getCheckQuantity());
                        updateInventoryList.add(inventory);
                    }
                }
            }else{
                wrapper.eq(Inventory::getSkuId,detail.getSkuId());
                wrapper.eq(Inventory::getWarehouseId,detail.getWarehouseId());
                Inventory inventory = inventoryMapper.selectOne(wrapper);
                if(inventory != null){
                    ItemSkuMapVo itemSkuMapVo = itemSkuService.queryItemSkuMapVo(detail.getSkuId());
                    throw new ServiceException(
                        "账面库存不匹配："+itemSkuMapVo.getItem().getItemName()+"（"+itemSkuMapVo.getItemSku().getSkuName()+"）",
                        HttpStatus.CONFLICT,
                        "填写账面库存：0, 实际账面库存："+inventory.getQuantity());
                }else {
                    inventory = new Inventory();
                    inventory.setSkuId(detail.getSkuId());
                    inventory.setWarehouseId(detail.getWarehouseId());
                    inventory.setQuantity(detail.getCheckQuantity());
                    insertInventoryList.add(inventory);
                }
            }
        });
        if(CollUtil.isNotEmpty(updateInventoryList)){
            inventoryMapper.updateBatchById(updateInventoryList);
        }
        if(CollUtil.isNotEmpty(insertInventoryList)){
            inventoryMapper.insertBatch(insertInventoryList);
        }
    }

    @Transactional
    public void add(List<? extends BaseOrderDetailBo> details) {
        List<Inventory> addList = new LinkedList<>();
        List<Inventory> updateList = new LinkedList<>();
        details.forEach(orderDetailsBo -> {
            LambdaQueryWrapper<Inventory> wrapper = Wrappers.lambdaQuery();
            wrapper.eq(Inventory::getWarehouseId, orderDetailsBo.getWarehouseId());
            wrapper.eq(Inventory::getSkuId, orderDetailsBo.getSkuId());
            Inventory result = inventoryMapper.selectOne(wrapper);
            if(result!=null){
                BigDecimal before = result.getQuantity();
                BigDecimal after = before.add(orderDetailsBo.getQuantity());
                result.setQuantity(after);
                orderDetailsBo.setAfterQuantity(after);
                orderDetailsBo.setBeforeQuantity(before);
                updateList.add(result);
            }else {
                orderDetailsBo.setBeforeQuantity(BigDecimal.ZERO);
                orderDetailsBo.setAfterQuantity(orderDetailsBo.getQuantity());
                Inventory inventory = MapstructUtils.convert(orderDetailsBo, Inventory.class);
                addList.add(inventory);
            }
        });
        if (addList.size() > 0) {
            saveBatch(addList);
        }
        if (updateList.size() > 0) {
            updateBatchById(updateList);
        }
    }

    /**
     * 扣减库存
     * @param details
     */
    @Transactional
    public void subtract(List<? extends BaseOrderDetailBo> details) {
        List<Inventory> updateList = new LinkedList<>();
        details.forEach(shipmentOrderDetailBo -> {
            LambdaQueryWrapper<Inventory> wrapper = Wrappers.lambdaQuery();
            wrapper.eq(Inventory::getWarehouseId, shipmentOrderDetailBo.getWarehouseId());
            wrapper.eq(Inventory::getSkuId, shipmentOrderDetailBo.getSkuId());
            Inventory result = inventoryMapper.selectOne(wrapper);
            if(result==null){
                ItemSkuMapVo itemSkuMapVo = itemSkuService.queryItemSkuMapVo(shipmentOrderDetailBo.getSkuId());
                throw new ServiceException("库存不足", HttpStatus.CONFLICT,itemSkuMapVo.getItem().getItemName()+"（"+itemSkuMapVo.getItemSku().getSkuName()+"）库存不足，当前库存：0");
            }
            BigDecimal beforeQuantity = result.getQuantity();
            BigDecimal afterQuantity = beforeQuantity.subtract(shipmentOrderDetailBo.getQuantity());
            if(afterQuantity.signum() == -1){
                ItemSkuMapVo itemSkuMapVo = itemSkuService.queryItemSkuMapVo(shipmentOrderDetailBo.getSkuId());
                throw new ServiceException("库存不足", HttpStatus.CONFLICT,itemSkuMapVo.getItem().getItemName()+"（"+itemSkuMapVo.getItemSku().getSkuName()+"）库存不足，当前库存："+ beforeQuantity);
            }
            shipmentOrderDetailBo.setBeforeQuantity(beforeQuantity);
            shipmentOrderDetailBo.setAfterQuantity(afterQuantity);
            result.setQuantity(afterQuantity);
            updateList.add(result);
        });
        updateBatchById(updateList);
    }

    public boolean existsByWarehouseId(Long warehouseId) {
        LambdaQueryWrapper<Inventory> lqw = Wrappers.lambdaQuery();
        lqw.eq(Inventory::getWarehouseId, warehouseId);
        return inventoryMapper.exists(lqw);
    }
}
