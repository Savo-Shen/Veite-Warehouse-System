package com.ruoyi.wms.mapper;

import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.entity.EcCategory;
import com.ruoyi.wms.domain.vo.EcCategoryVo;

import java.util.List;

/**
 * 电商类目Mapper接口
 *
 * @author savo
 * @date 2026-08-23
 */
public interface EcCategoryMapper extends BaseMapperPlus<EcCategory, EcCategoryVo> {

    /** 带商品计数的类目列表 */
    List<EcCategoryVo> selectWithCount();
}
