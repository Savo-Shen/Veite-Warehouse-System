package com.ruoyi.wms.domain.vo;

import com.ruoyi.common.mybatis.core.domain.BaseVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 胶管分段视图 —— 一段一行
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HosePieceVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 关联 wms_item_sku.id */
    private Long skuId;
    private String hoseCode;
    private BigDecimal lengthM;
    private String status;
    private String remark;

    private Long locationId;
    private String locationCode;
    private String locationName;

    /** 规格附带信息，列表页直接展示 */
    private String nickname;
    private String inch;
    private String layerName;
}
