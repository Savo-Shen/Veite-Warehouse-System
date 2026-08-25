package com.ruoyi.wms.mapper;

import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.entity.HosePiece;
import com.ruoyi.wms.domain.vo.HosePieceVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 胶管分段Mapper
 *
 * @author savo
 * @date 2026-08-23
 */
public interface HosePieceMapper extends BaseMapperPlus<HosePiece, HosePieceVo> {

    /** 某规格的全部在库段，短的排前面 —— 优先切最短的够用段，省长料 */
    List<HosePieceVo> selectInStock(@Param("hoseCode") String hoseCode);

    /** 全部在库段（列表页用） */
    List<HosePieceVo> selectAllInStock(@Param("keyword") String keyword);
}
