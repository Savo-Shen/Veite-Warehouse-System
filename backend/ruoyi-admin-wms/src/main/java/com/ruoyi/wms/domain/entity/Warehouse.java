package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_warehouse")
public class Warehouse extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 编号
     */
    private String warehouseCode;

    /**
     * 名称
     */
    private String warehouseName;
    /**
     * 地址
     */
    private String address;
    /**
     * 经度（GCJ-02）
     */
    private BigDecimal longitude;
    /**
     * 纬度（GCJ-02）
     */
    private BigDecimal latitude;
    /**
     * 排序
     */
    private Long orderNum;

    /**
     * 备注
     */
    private String remark;

    /**
     * 货架3D布局（JSON，前端货架示意图使用）
     */
    private String shelfLayout;

}
