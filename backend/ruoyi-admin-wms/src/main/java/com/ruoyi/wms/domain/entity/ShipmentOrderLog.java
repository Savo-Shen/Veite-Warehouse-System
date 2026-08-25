package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseHistoryEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 出库单变更历史对象 wms_shipment_order_log
 *
 * 单据表上的 update_by / update_time 只留得住「最后一次是谁改的」，
 * 这张表补上「改了什么、之前是多少」——回头查价格时真正要看的就是这个。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_shipment_order_log")
public class ShipmentOrderLog extends BaseHistoryEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 出库单id
     */
    private Long orderId;

    /**
     * 出库单号。单据被删掉后仍能看出这条历史属于哪张单
     */
    private String orderNo;

    /**
     * 动作：CREATE 新建 / UPDATE 修改 / SHIPMENT 出库 / VOID 作废
     */
    private String action;

    /**
     * 变更摘要，人类可读，直接展示不做解析
     */
    private String summary;

    /**
     * 操作人
     */
    private String createBy;
}
