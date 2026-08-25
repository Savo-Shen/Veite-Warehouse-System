package com.ruoyi.wms.domain.vo;

import com.ruoyi.common.mybatis.core.domain.BaseVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 电商类目视图对象 ec_category
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EcCategoryVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String ecCode;
    private String ecL1;
    private String ecL2;
    private String pddCatId;
    private String pddCatPath;
    private String tbCatId;
    private String tbCatPath;
    private String dyCatId;
    private String dyCatPath;
    private Integer orderNum;
    private String remark;

    /** 归属该类目的电商商品数 */
    private Integer productCount;
}
