package com.ruoyi.wms.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.wms.domain.entity.ShipmentOrderLog;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 出库单变更历史视图对象 wms_shipment_order_log
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ShipmentOrderLog.class)
public class ShipmentOrderLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private String orderNo;

    /**
     * 动作：CREATE / UPDATE / SHIPMENT / VOID
     */
    private String action;

    /**
     * 变更摘要
     */
    private String summary;

    /**
     * 操作人
     */
    private String createBy;

    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
