package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 胶管规格 wms_hose_spec
 * <p>
 * 主键是现场那套 4 位码：前两位内径mm + 后两位层数，例 1302 = 内径13(1/2" 四分) 二层钢丝。
 * 不用 DN —— 现场从来不这么叫，1/2" 他们说 13 不说 DN12。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_hose_spec")
public class HoseSpec extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 现场 4 位码，如 1302 */
    private String hoseCode;

    /** 内径码 06/08/10/13/16/19/22/25/32/38/51 */
    private String boreCode;

    /** 层数码 01/02/03/04/06/00 */
    private String layerCode;

    private String inch;

    /** 俗称，如「四分」。客户电话里说的是这个 */
    private String nickname;

    private String layerName;

    private BigDecimal idMm;

    /** 欧标参考值，22 通径不在标准序列内所以为空 */
    private BigDecimal odMm;

    private BigDecimal workPressureMpa;

    private Integer bendRadiusMm;

    /** 元/米 */
    private BigDecimal costPrice;

    /** 实价 / 推算。推算值误差按 ±15% 看 */
    private String priceSource;

    private String stdRef;

    private String remark;
}
