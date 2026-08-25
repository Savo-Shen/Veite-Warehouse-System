package com.ruoyi.wms.domain.vo;

import com.ruoyi.common.mybatis.core.domain.BaseVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 胶管接头视图
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HoseFittingVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 关联 wms_item_sku.id */
    private Long skuId;
    private String fittingSku;
    private String fieldName;
    private String threadSystem;
    private String threadSpec;
    private String seatType;
    private String sealStd;
    private String stdCode;
    private String gender;
    private String angle;
    private String boreHint;
    private Integer seenOnSheet;

    /** NULL = 还没盘，0 = 盘过确认没有。前端要分开显示 */
    private Integer qty;

    private Long locationId;
    private String locationCode;
    private String locationName;
    private BigDecimal costPrice;
    private String brand;
    private String vendorCode;
    private String remark;
    private java.math.BigDecimal sellingPrice;
}
