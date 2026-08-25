package com.ruoyi.wms.domain.bo;

import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 电商商品业务对象 ec_product
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EcProductBo extends BaseEntity {

    @NotNull(message = "主键不能为空", groups = { EditGroup.class })
    private Long id;

    /** 列表查询：商品名 / 类目 / 品牌 模糊匹配 */
    private String keyword;

    /** 列表查询：按电商类目过滤 */
    private Long ecCategoryId;

    /** 列表查询：按状态过滤 */
    private String status;

    /** 列表查询：只看首发批次（售价齐、品牌齐、SKU≤10） */
    private Boolean onlyReady;

    @Size(max = 120, message = "标题不能超过 120 个字符")
    private String ecTitle;

    @Size(max = 500, message = "卖点不能超过 500 个字符")
    private String sellingPoints;

    private String attrs;

    private String remark;
}
