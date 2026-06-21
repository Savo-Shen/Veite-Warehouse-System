package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 商品标签对象 wms_item_tag
 *
 * @author savo
 * @date 2026-06-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_item_tag")
public class ItemTag extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签颜色（十六进制，如 #409EFF）
     */
    private String color;

    /**
     * 备注
     */
    private String remark;

}
