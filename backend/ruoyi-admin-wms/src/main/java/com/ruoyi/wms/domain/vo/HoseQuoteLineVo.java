package com.ruoyi.wms.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 配料单里的一行：一根总成 = 1 段胶管 + 2×(接头 + 扣压外套)，所以正常是 5 行。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class HoseQuoteLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 胶管 / A端接头 / A端外套 / B端接头 / B端外套 */
    private String role;

    /** 编码：胶管 4 位码、接头 SKU、外套 SKU */
    private String code;

    /** 现场叫法，师傅照着这个去货架上找 */
    private String name;

    /** 补充规格说明 */
    private String spec;

    /** 需要多少，如「12 米」「2 个」 */
    private String needText;

    /** 在库情况，如「最长一段 20 米（共 3 段 34 米）」「8 个」「未盘点」 */
    private String stockText;

    /** 库位，师傅要走到哪去拿 */
    private String locationText;

    /** 够 / 缺 / 未盘 / 无档案 */
    private String status;

    /** 单价（胶管是元/米，接头外套是元/个） */
    private BigDecimal unitCost;

    /** 小计。单价没录时为 null */
    private BigDecimal amount;
}
