package com.ruoyi.wms.domain.bo;

import com.ruoyi.common.core.validate.AddGroup;
import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
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
     * 商品信息
     */
    private List<T> details;
}
