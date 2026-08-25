package com.ruoyi.wms.domain.bo;

import com.ruoyi.common.core.validate.AddGroup;
import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.wms.domain.entity.ShipmentOrder;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 出库单业务对象 wms_shipment_order
 *
 * @author zcc
 * @date 2024-08-01
 */

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ShipmentOrder.class, reverseConvertGenerate = false)
public class ShipmentOrderBo extends BaseOrderBo<ShipmentOrderDetailBo> {
    /**
     * 入库类型
     */
    @NotNull(message = "出库类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long optType;

    /**
     * 纯记录单：只留价格备查，不扣库存、不写库存流水，商品名手工输入不挂 SKU
     */
    private Boolean recordOnly;

    /**
     * 订单号
     */
    private String bizOrderNo;
    /**
     * 对接商户
     */
    private Long merchantId;
    /**
     * 仓库id
     */
    @NotNull(message = "仓库不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long warehouseId;

    /**
     * 允许扣成负库存。前端在「库存不足」提示里二次确认后才会传 true，
     * 且还要系统参数 wms.inventory.allowNegative 打开才真正生效。不入库表，仅用于本次出库。
     */
    private Boolean allowNegative;
}
