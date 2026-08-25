package com.ruoyi.wms.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 配料查询入参：接一根什么样的总成。
 * <p>
 * 两端接头分开填 —— 现实里两头不一样的居多（一头公制24°、一头英制60°）。
 * 只填 A 端时 B 端按同款处理。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class HoseQuoteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 胶管 4 位码，如 1302。前端从下拉里选，等于同时定了通径和层数 */
    @NotBlank(message = "请选择胶管规格")
    private String hoseCode;

    /** 要多长（米） */
    @NotNull(message = "请填长度")
    private BigDecimal lengthM;

    /** A 端接头 SKU */
    private String endASku;

    /** B 端接头 SKU；不填就按 A 端同款 */
    private String endBSku;

    /** 外套剥皮方式：非剥皮 / 剥皮。不填按非剥皮（最常用） */
    private String skinType;

    /** 做几根，默认 1 */
    private Integer assemblyQty;
}
