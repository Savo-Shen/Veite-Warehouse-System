package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 电商商品档案对象 ec_product
 * <p>
 * wms_item 按品牌拆行（同一个 SDA 薄型气缸有 13 个品牌各一行），电商侧应当合并成一个
 * 商品、品牌作销售属性。本表即电商侧的「一个商品」，通过 ec_product_item 关联多个 wms_item。
 * 电商专属字段（长标题、卖点、属性）放这里，不进 wms_item 那张打单热表。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ec_product")
public class EcProduct extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 归组名，取自 wms_item.item_name */
    private String ecName;

    /** 电商类目，关联 ec_category.id */
    private Long ecCategoryId;

    /** 电商长标题，30-60 字 */
    private String ecTitle;

    /** 卖点，换行分隔 */
    private String sellingPoints;

    /** 结构化属性 JSON（材质/接口螺纹/工作压力/温度范围等），以字符串承载 */
    private String attrs;

    /** 待整理 / 待拍图 / 可上架 / 已上架 */
    private String status;

    private String remark;
}
