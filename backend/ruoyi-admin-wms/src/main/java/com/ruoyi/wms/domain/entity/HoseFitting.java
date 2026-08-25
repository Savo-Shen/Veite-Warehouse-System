package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 胶管接头 wms_hose_fitting
 * <p>
 * 编码跟现场手写标签一一对应：螺纹 + 型 + [弯] + 芯/面。
 * 例 22×1.5 A型芯 → M22x1.5-A-芯，18×1.5 C型弯面 → M18x1.5-C-弯-面。
 * <p>
 * 型 A/C/D 是接头内部密封座的形状（现场码），不是弯头角度 —— 这点容易搞错。
 * 配管通径只能当参考：公制螺纹有轻/重系列两解，M18×1.5 既是轻系列配三分管、
 * 也是重系列配二分半管，光看标签分不出来，所以它不进编码也不做筛选条件。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_hose_fitting")
public class HoseFitting extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 关联 wms_item_sku.id。库存/库位/进价都在主商品体系里，本表只存业务属性 */
    private Long skuId;

    private String fittingSku;

    /** 现场叫法，跟手写标签一字不差 */
    private String fieldName;

    /** 公制 / 英制 / 美制 */
    private String threadSystem;

    private String threadSpec;

    /** A / C / D */
    private String seatType;

    private String sealStd;

    private String stdCode;

    /** 芯=公头(外螺纹) / 面=母头(内螺纹) */
    private String gender;

    /** 直 / 弯 */
    private String angle;

    private String boreHint;

    private Integer seenOnSheet;





    /** 厂家代号（20111 这类五位码），各厂不一致，按实物填 */
    private String vendorCode;

    private String remark;
}
