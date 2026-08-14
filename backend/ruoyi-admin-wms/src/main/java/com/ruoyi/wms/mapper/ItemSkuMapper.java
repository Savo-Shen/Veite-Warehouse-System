package com.ruoyi.wms.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.entity.ItemSku;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.domain.vo.ItemSkuVo;
import com.ruoyi.wms.domain.vo.LocationVo;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface ItemSkuMapper extends BaseMapperPlus<ItemSku, ItemSkuVo> {

    IPage<ItemSkuMapVo> selectByBo(
        IPage<ItemSkuVo> page, 
        @Param("bo") ItemSkuBo bo,
        @Param("itemKeywordGroups") List<List<String>> itemKeywordGroups
    );

    List<ItemSkuMapVo> queryItemSkuMapVos(@Param("ids") Collection<Long> ids);

    ItemSkuMapVo queryItemSkuMapVo(@Param("id") Long id);

    LocationVo queryItemLocationVo(Long id);

    int updateItemLocationIdById(@Param("id") Long id, @Param("itemLocationId") String itemLocationId);

    /**
     * 综合搜索：按关键字（规格名称/规格编号/条码/商品名称/商品编号）模糊匹配，返回命中的规格(sku) id 列表。
     */
    List<Long> selectIdsByKeyword(@Param("word") String word);
}
