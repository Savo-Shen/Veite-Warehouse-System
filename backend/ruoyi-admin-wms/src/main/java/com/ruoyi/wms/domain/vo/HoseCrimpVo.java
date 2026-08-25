package com.ruoyi.wms.domain.vo;

import com.ruoyi.common.mybatis.core.domain.BaseVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 扣压参数视图
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HoseCrimpVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String layerScope;
    private String boreCode;
    private BigDecimal crimpDiameterMm;
    private String dieNo;
    private BigDecimal stripLengthMm;
    private BigDecimal insertDepthMm;
    private String pressGear;
    private Integer shopCanCrimp;
    private String remark;

    private String nickname;
    private String inch;
}
