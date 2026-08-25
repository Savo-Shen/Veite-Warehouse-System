package com.ruoyi.wms.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.bo.EcProductBo;
import com.ruoyi.wms.domain.entity.EcProduct;
import com.ruoyi.wms.domain.vo.EcListingSkuVo;
import com.ruoyi.wms.domain.vo.EcProductVo;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 电商商品Mapper接口
 *
 * @author savo
 * @date 2026-08-23
 */
public interface EcProductMapper extends BaseMapperPlus<EcProduct, EcProductVo> {

    /** 上新列表：一行一个电商商品，附带就绪度计数 */
    IPage<EcProductVo> selectListingPage(IPage<EcProductVo> page, @Param("bo") EcProductBo bo);

    /** 按 ID 批量取上新视图（不分页），供 AI 批量生成标题时组装提示词 */
    List<EcProductVo> selectListingByIds(@Param("ids") Collection<Long> ids);

    /** 某个电商商品下的全部 SKU 明细 */
    List<EcListingSkuVo> selectSkusByProductId(@Param("productId") Long productId);
}
