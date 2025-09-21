package com.ruoyi.wms.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.ruoyi.wms.domain.entity.Location;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Location.class)
public class LocationVo implements Serializable {

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
    private String locationCode;

    /**
     * 名称
     */
    @ExcelProperty(value = "名称")
    private String locationName;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

}
