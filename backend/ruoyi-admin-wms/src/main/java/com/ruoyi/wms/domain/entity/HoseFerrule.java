package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 扣压外套（皮子）wms_hose_ferrule
 * <p>
 * 三层管外径跟一二层不同，外套不通用，所以 layer_scope 里三层是单独一档。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_hose_ferrule")
public class HoseFerrule extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 关联 wms_item_sku.id。库存/库位/进价都在主商品体系里，本表只存业务属性 */
    private Long skuId;

    private String ferruleSku;

    private String ferruleName;

    /** 1层/2层、3层、4层、6层 */
    private String layerScope;

    private String boreCode;

    /** 非剥皮 / 剥皮 / 不分 */
    private String skinType;




    private String remark;
}
