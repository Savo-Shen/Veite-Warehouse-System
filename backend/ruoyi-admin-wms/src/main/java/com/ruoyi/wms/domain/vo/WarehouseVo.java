package com.ruoyi.wms.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.ruoyi.common.mybatis.core.domain.BaseVo;
import com.ruoyi.wms.domain.entity.Warehouse;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.math.BigDecimal;


@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Warehouse.class)
public class WarehouseVo extends BaseVo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    @ExcelProperty(value = "")
    private Long id;

    /**
     * 编号
     */
    @ExcelProperty(value = "编号")
    private String warehouseCode;

    /**
     * 名称
     */
    @ExcelProperty(value = "名称")
    private String warehouseName;
    /**
     * 地址
     */
    @ExcelProperty(value = "地址")
    private String address;
    /**
     * 经度（GCJ-02）
     */
    @ExcelProperty(value = "经度")
    private BigDecimal longitude;
    /**
     * 纬度（GCJ-02）
     */
    @ExcelProperty(value = "纬度")
    private BigDecimal latitude;
    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Long orderNum;
    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 货架3D布局（JSON）
     */
    private String shelfLayout;

}
