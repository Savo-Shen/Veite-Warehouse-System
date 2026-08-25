package com.ruoyi.wms.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 电商上新明细中的单个 SKU 视图对象
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class EcListingSkuVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long skuId;
    private String skuName;
    private String brandName;
    private String unit;

    private BigDecimal costPrice;
    private BigDecimal sellingPrice;

    /** 以下五项必须实测，查不到也不该推算——直接决定运费 */
    private BigDecimal netWeight;
    private BigDecimal grossWeight;
    private BigDecimal packLength;
    private BigDecimal packWidth;
    private BigDecimal packHeight;

    /** 解析出的规格参数 JSON */
    private String spec;
    private String confidence;
}
