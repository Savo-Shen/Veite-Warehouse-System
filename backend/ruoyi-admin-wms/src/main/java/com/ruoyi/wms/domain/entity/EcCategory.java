package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 电商类目对象 ec_category
 * <p>
 * 仓库类目最深 4 层、叶子是型号系列（拣货按型号翻）；电商类目最深 3 层、叶子到
 * 「气动接头」就停，型号是 SPU 和标题关键词。两者形状不同，故不合并成一棵树，
 * 由 wms_item_category.ec_category_id 做映射。
 * 三个平台的类目 ID 需到各自商家后台搜到叶子类目后回填。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ec_category")
public class EcCategory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    private String ecCode;

    /** 电商一级类目 */
    private String ecL1;

    /** 电商二级类目（叶子） */
    private String ecL2;

    private String pddCatId;
    private String pddCatPath;
    private String tbCatId;
    private String tbCatPath;
    private String dyCatId;
    private String dyCatPath;

    private Integer orderNum;
    private String remark;
}
