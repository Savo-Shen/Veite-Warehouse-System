package com.ruoyi.wms.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 盘点回填：接头 / 外套的库存、库位、成本价。
 * <p>
 * qty 传 null 表示「不动这个字段」，传 0 表示「盘过，确认没有」。
 * 这两者含义不同，不要在前端把空输入当 0 提交。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class HoseStockBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "主键不能为空")
    private Long id;

    private Integer qty;

    private Long locationId;

    private BigDecimal costPrice;

    private String brand;

    private String vendorCode;

    private String remark;
}
