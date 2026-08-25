package com.ruoyi.wms.mapper;

import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.entity.HoseCrimp;
import com.ruoyi.wms.domain.vo.HoseCrimpVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 扣压参数Mapper
 *
 * @author savo
 * @date 2026-08-23
 */
public interface HoseCrimpMapper extends BaseMapperPlus<HoseCrimp, HoseCrimpVo> {

    List<HoseCrimpVo> selectCrimpList();

    HoseCrimpVo selectOneFor(@Param("layerScope") String layerScope,
                             @Param("boreCode") String boreCode);
}
