package com.ruoyi.wms.mapper;

import com.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import com.ruoyi.wms.domain.entity.HosePiece;
import com.ruoyi.wms.domain.vo.HosePieceVo;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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

    /** 某 SKU 在库分段之和。分段是唯一事实来源，wms_inventory.quantity 由它算出来 */
    BigDecimal sumInStock(@Param("skuId") Long skuId);

    /** 改数量，返回影响行数；为 0 说明还没有库存行 */
    int updateInventoryQty(@Param("skuId") Long skuId, @Param("total") BigDecimal total);

    /** 建库存行（已存在则什么都不做） */
    void insertInventory(@Param("skuId") Long skuId, @Param("total") BigDecimal total);
}
