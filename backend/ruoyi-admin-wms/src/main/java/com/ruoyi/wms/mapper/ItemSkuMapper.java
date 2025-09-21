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
        @Param("itemKeywords") String itemKeywords
    );

    List<ItemSkuMapVo> queryItemSkuMapVos(Collection<Long> ids);

    ItemSkuMapVo queryItemSkuMapVo(Long id);

    LocationVo queryItemLocationVo(Long id);

    int updateItemLocationIdById(@Param("id") Long id, @Param("itemLocationId") String itemLocationId);
}
