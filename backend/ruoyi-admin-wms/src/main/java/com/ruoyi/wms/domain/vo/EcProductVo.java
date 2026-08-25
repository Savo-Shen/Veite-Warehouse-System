package com.ruoyi.wms.domain.vo;

import com.ruoyi.common.mybatis.core.domain.BaseVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 电商上新列表视图对象
 * <p>
 * 一行 = 一个电商商品，附带上架就绪度所需的各项计数。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EcProductVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String ecName;
    private String ecTitle;
    private String sellingPoints;
    private String attrs;
    private String status;
    private String remark;

    private Long ecCategoryId;
    /** 「气动元件 › 气动接头」 */
    private String ecCategoryPath;
    /** 平台类目 ID，空表示尚未到商家后台回填 */
    private String pddCatId;
    private String tbCatId;
    private String dyCatId;

    /** 该商品下所有品牌，斜杠分隔 */
    private String brands;
    /** 关联的 wms_item 行数（同名不同品牌） */
    private Integer itemCount;

    private Integer skuCount;
    private Integer noPriceCount;
    private Integer noCostCount;
    private Integer weighedCount;
    private Integer packedCount;
    private Integer mediaCount;
}
