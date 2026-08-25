package com.ruoyi.wms.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.bo.HoseFittingBo;
import com.ruoyi.wms.domain.entity.HoseFitting;
import com.ruoyi.wms.domain.vo.HoseFittingVo;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 胶管接头Mapper
 *
 * @author savo
 * @date 2026-08-23
 */
public interface HoseFittingMapper extends BaseMapperPlus<HoseFitting, HoseFittingVo> {

    /** 接头列表（带库位名） */
    IPage<HoseFittingVo> selectFittingPage(IPage<HoseFittingVo> page, @Param("bo") HoseFittingBo bo);

    /** 按 SKU 批量取，配料时一次取两端 */
    List<HoseFittingVo> selectBySkus(@Param("skus") Collection<String> skus);
}
