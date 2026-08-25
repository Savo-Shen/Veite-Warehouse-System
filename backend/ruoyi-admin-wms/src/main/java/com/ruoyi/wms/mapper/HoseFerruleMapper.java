package com.ruoyi.wms.mapper;

import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.entity.HoseFerrule;
import com.ruoyi.wms.domain.vo.HoseFerruleVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 扣压外套Mapper
 *
 * @author savo
 * @date 2026-08-23
 */
public interface HoseFerruleMapper extends BaseMapperPlus<HoseFerrule, HoseFerruleVo> {

    /** 外套列表（带库位名与通径俗称） */
    List<HoseFerruleVo> selectFerruleList(@Param("keyword") String keyword);

    /**
     * 配料用：按层数档 + 通径 + 剥皮方式取一个。
     * 三层管外径跟一二层不同，外套不通用，所以 layerScope 必须精确匹配。
     */
    HoseFerruleVo selectOneFor(@Param("layerScope") String layerScope,
                               @Param("boreCode") String boreCode,
                               @Param("skinType") String skinType);
}
