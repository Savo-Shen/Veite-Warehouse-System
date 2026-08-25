package com.ruoyi.wms.mapper;

import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.entity.HoseSpec;
import com.ruoyi.wms.domain.vo.HoseSpecVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 胶管规格Mapper
 *
 * @author savo
 * @date 2026-08-23
 */
public interface HoseSpecMapper extends BaseMapperPlus<HoseSpec, HoseSpecVo> {

    /**
     * 规格 + 在库汇总。maxLengthM 是「能不能接一根 N 米的」的判据，
     * totalLengthM 只能看，不能拿来判断。
     *
     * @param onlyInStock true 时只返回有在库段的规格
     */
    List<HoseSpecVo> selectWithStock(@Param("keyword") String keyword,
                                     @Param("onlyInStock") Boolean onlyInStock);

    /** 单个规格 + 在库汇总 */
    HoseSpecVo selectOneWithStock(@Param("hoseCode") String hoseCode);
}
