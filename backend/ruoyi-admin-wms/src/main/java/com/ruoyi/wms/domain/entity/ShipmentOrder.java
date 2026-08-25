package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/**
 * 出库单对象 wms_shipment_order
 *
 * @author zcc
 * @date 2024-08-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_shipment_order")
public class ShipmentOrder extends BaseOrder {
    /**
     * 仓库id
     */
    private Long warehouseId;

    /**
     * 入库类型
     */
    private Long optType;
    /**
     * 纯记录单：只留价格备查，不扣库存、不写库存流水，商品名手工输入不挂 SKU
     */
    private Boolean recordOnly;

    /**
     * 业务订单号
     */
    private String bizOrderNo;
    /**
     * 供应商
     */
    private Long merchantId;

    /**
     * 补充图片 OSS ID，多个用逗号分隔
     */
    private String supplementImageIds;


    /**
     * 业务日期：这单实际发生在哪天。补前几天的单子时选过去的日期，
     * 列表、库存流水、看板统计都按它算；create_time 仍然是录入时间。
     */
    private LocalDate bizDate;
}
