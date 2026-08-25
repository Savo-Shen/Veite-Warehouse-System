package com.ruoyi.wms.domain.vo;

import com.ruoyi.common.mybatis.core.domain.BaseVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 扣压外套视图
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HoseFerruleVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 关联 wms_item_sku.id */
    private Long skuId;
    private String ferruleSku;
    private String ferruleName;
    private String layerScope;
    private String boreCode;
    private String skinType;
    private Integer qty;

    private Long locationId;
    private String locationCode;
    private String locationName;
    private BigDecimal costPrice;
    private String remark;

    /** 通径附带信息 */
    private String nickname;
    private String inch;
}
