package com.ruoyi.wms.domain.bo;

import com.ruoyi.common.core.validate.AddGroup;
import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseOrderBo<T extends BaseOrderDetailBo> extends BaseEntity {
    /**
     * 手机端快捷搜索：匹配系统单号或业务单号
     */
    private String keyword;

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 业务单号
     */
    @NotBlank(message = "入库单号单号不能为空", groups = { AddGroup.class, EditGroup.class })
    private String orderNo;

    /**
     * 商品总数
     */
    private BigDecimal totalQuantity;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态
     */
    private Integer orderStatus;

    /**
     * 备注
     */
    private String remark;

    /**
     * 补充图片 OSS ID，多个用逗号分隔
     */
    private String supplementImageIds;

    /**
     * 业务日期：这单实际发生在哪天。补前几天的单子时选过去的日期，
     * 列表、库存流水、看板统计都按它算；create_time 仍然是录入时间。
     * 只有出入库单的表里有这一列，移库/盘点不传，走当天。
     */
    private LocalDate bizDate;

    /**
     * 商品信息
     */
    private List<T> details;
}
