package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 胶管分段库存 wms_hose_piece —— 一段一行
 * <p>
 * 余料不能合并使用。1602 合计 32 米，实际是 10+10+4+8 四段，
 * 接一张 12 米的单子一段都不够。所以「够不够」的判据是
 * MAX(length_m) >= 需求，不是 SUM(length_m) >= 需求。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_hose_piece")
public class HosePiece extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 关联 wms_item_sku.id。库存/库位/进价都在主商品体系里，本表只存业务属性 */
    private Long skuId;

    private String hoseCode;

    private Long locationId;

    /** 这一段的长度（米） */
    private BigDecimal lengthM;

    /** 在库 / 已用完 */
    private String status;

    private String remark;
}
