package com.ruoyi.wms.domain.vo;

import com.ruoyi.common.mybatis.core.domain.BaseVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 胶管规格视图 —— 规格档案 + 实时库存汇总
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HoseSpecVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String hoseCode;
    private String boreCode;
    private String layerCode;
    private String inch;
    private String nickname;
    private String layerName;
    private BigDecimal idMm;
    private BigDecimal odMm;
    private BigDecimal workPressureMpa;
    private Integer bendRadiusMm;
    private BigDecimal costPrice;
    private String priceSource;
    private String stdRef;
    private String remark;

    /** 在库总米数。只做展示，判断够不够要看 maxLengthM */
    private BigDecimal totalLengthM;

    /** 在库段数 */
    private Integer pieceCount;

    /** 最长的一段有多少米 —— 余料不能接，这个才是「能不能接一根 N 米的」的判据 */
    private BigDecimal maxLengthM;

    /** 分段明细文本，如「10+10+8+4」，让人一眼看出零碎程度 */
    private String pieceText;

    /** 存放库位，多个用顿号连接 */
    private String locationNames;
}
