package com.ruoyi.wms.domain.bo;

import com.ruoyi.wms.domain.entity.ShipmentOrderDetail;
import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMappers;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 出库单详情业务对象 wms_shipment_order_detail
 *
 * @author zcc
 * @date 2024-08-01
 */

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMappers({
    @AutoMapper(target = ShipmentOrderDetail.class, reverseConvertGenerate = false),
    @AutoMapper(target = InventoryBo.class, reverseConvertGenerate = false)
})
public class ShipmentOrderDetailBo extends BaseOrderDetailBo{

    /**
     * 商品名称。纯记录单手工输入，此时 skuId 为空，只能靠这列显示
     */
    private String itemName;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 销售价。正常出库单即原来那个「单价」，以前没落库靠 amount/quantity 反推
     */
    private BigDecimal salePrice;
}
