package com.ruoyi.wms.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.bo.InventoryBo;
import com.ruoyi.wms.domain.entity.Inventory;
import com.ruoyi.wms.domain.vo.InventoryVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存Mapper接口
 *
 * @author zcc
 * @date 2024-07-19
 */
public interface InventoryMapper extends BaseMapperPlus<Inventory, InventoryVo> {

    Page<InventoryVo> queryItemBoardList(
        Page<InventoryVo> page, 
        @Param("bo") InventoryBo bo,
        @Param("itemKeywordGroups") List<List<String>> itemKeywordGroups
    );
    Page<InventoryVo> queryWarehouseBoardList(
        Page<InventoryVo> page,
        @Param("bo") InventoryBo bo,
        @Param("itemKeywordGroups") List<List<String>> itemKeywordGroups
    );

    /**
     * 查询用于导出的库存列表（不分页，复用看板查询条件）
     */
    List<InventoryVo> queryExportList(
        @Param("bo") InventoryBo bo,
        @Param("itemKeywordGroups") List<List<String>> itemKeywordGroups
    );

}
