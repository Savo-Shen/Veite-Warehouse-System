package com.ruoyi.wms.mapper;

import com.ruoyi.wms.domain.entity.ShipmentOrderDetail;
import com.ruoyi.wms.domain.vo.ShipmentOrderDetailVo;
import com.ruoyi.wms.domain.vo.SkuLastPriceVo;
import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 出库单详情Mapper接口
 *
 * @author zcc
 * @date 2024-08-01
 */
public interface ShipmentOrderDetailMapper extends BaseMapperPlus<ShipmentOrderDetail, ShipmentOrderDetailVo> {

    /**
     * 查询客户已出库的商品出库记录，按出库时间倒序
     */
    List<SkuLastPriceVo> queryRecentPriceList(@Param("merchantId") Long merchantId, @Param("skuIds") Collection<Long> skuIds);
}
