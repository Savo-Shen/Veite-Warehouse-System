package com.ruoyi.wms.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 实测数据录入（重量与包装尺寸）
 * <p>
 * 这五项查不到也不该推算：重量没有权威目录，包装尺寸取决于自身打包方式，
 * 而两者直接决定运费。只接受人工实测录入。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class EcSkuMeasureBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    private BigDecimal netWeight;
    private BigDecimal grossWeight;
    private BigDecimal packLength;
    private BigDecimal packWidth;
    private BigDecimal packHeight;

    /** 顺带改价，为 null 时不动 */
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
}
