package com.ruoyi.wms.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 接头列表筛选条件
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class HoseFittingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模糊匹配现场叫法 / SKU / 螺纹规格 */
    private String keyword;

    /** 公制 / 英制 / 美制 */
    private String threadSystem;

    /** A / C / D */
    private String seatType;

    /** 芯 / 面 */
    private String gender;

    /** 直 / 弯 */
    private String angle;

    /** true = 只看手写盘点纸上出现过的（现场真的在货的那批） */
    private Boolean onlySeen;

    /** true = 只看有库存的 */
    private Boolean onlyInStock;
}
