package com.ruoyi.wms.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.ruoyi.wms.domain.entity.ReceiptOrder;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/**
 * 入库单视图对象 wms_receipt_order
 *
 * @author zcc
 * @date 2024-07-19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ExcelIgnoreUnannotated
@AutoMapper(target = ReceiptOrder.class)
public class ReceiptOrderVo extends BaseOrderVo<ReceiptOrderDetailVo> {

    /**
     * 入库类型
     */
    @ExcelProperty(value = "操作类型")
    private Long optType;

    /**
     * 供应商
     */
    @ExcelProperty(value = "对接商家id")
    private Long merchantId;

    /**
     * 订单号
     */
    @ExcelProperty(value = "业务订单号")
    private String bizOrderNo;

    /**
     * 业务日期：这单实际发生在哪天。补前几天的单子时选过去的日期，
     * 列表、库存流水、看板统计都按它算；create_time 仍然是录入时间。
     */
    private LocalDate bizDate;
}
