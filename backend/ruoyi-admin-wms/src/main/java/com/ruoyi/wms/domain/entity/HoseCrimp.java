package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 扣压参数 wms_hose_crimp —— 教程里「压」那一步要看的数
 * <p>
 * 扣压直径、模具号、剥胶长度、插入深度都是机器和厂牌相关的，只能现场实测，
 * 建表时先按「适用层数 × 通径」铺空行占位。
 * shopCanCrimp 是店里那台压机压不压得动这一档，压不动就提示去仓库压。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_hose_crimp")
public class HoseCrimp extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String layerScope;

    private String boreCode;

    private BigDecimal crimpDiameterMm;

    private String dieNo;

    private BigDecimal stripLengthMm;

    private BigDecimal insertDepthMm;

    private String pressGear;

    /** 1 = 店里压机压得了；0 = 超出能力，要去仓库压 */
    private Integer shopCanCrimp;

    private String remark;
}
