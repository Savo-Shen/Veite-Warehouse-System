package com.ruoyi.wms.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadFontStyle;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.BooleanEnum;
import com.alibaba.excel.enums.poi.BorderStyleEnum;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import com.alibaba.excel.enums.poi.VerticalAlignmentEnum;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库存导出视图对象
 * <p>
 * 采用 EasyExcel 样式注解美化导出效果：深蓝表头 + 白色加粗字体，
 * 内容居中并带细边框，数字保留三位小数。
 *
 * @author savo
 * @date 2026-06-20
 */
@Data
@ExcelIgnoreUnannotated
@ColumnWidth(16)
// 表头样式：深蓝底色、居中、四周细边框
@HeadStyle(
    fillPatternType = FillPatternTypeEnum.SOLID_FOREGROUND,
    fillForegroundColor = 18,
    horizontalAlignment = HorizontalAlignmentEnum.CENTER,
    verticalAlignment = VerticalAlignmentEnum.CENTER,
    borderLeft = BorderStyleEnum.THIN,
    borderRight = BorderStyleEnum.THIN,
    borderTop = BorderStyleEnum.THIN,
    borderBottom = BorderStyleEnum.THIN
)
// 表头字体：白色、加粗、12 号
@HeadFontStyle(color = 9, bold = BooleanEnum.TRUE, fontHeightInPoints = 12)
// 内容样式：居中、四周细边框
@ContentStyle(
    horizontalAlignment = HorizontalAlignmentEnum.CENTER,
    verticalAlignment = VerticalAlignmentEnum.CENTER,
    borderLeft = BorderStyleEnum.THIN,
    borderRight = BorderStyleEnum.THIN,
    borderTop = BorderStyleEnum.THIN,
    borderBottom = BorderStyleEnum.THIN
)
public class InventoryExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品名称
     */
    @ColumnWidth(22)
    @ExcelProperty(value = "商品名称", index = 0)
    private String itemName;

    /**
     * 商品编号
     */
    @ExcelProperty(value = "商品编号", index = 1)
    private String itemCode;

    /**
     * 规格名称
     */
    @ColumnWidth(22)
    @ExcelProperty(value = "规格名称", index = 2)
    private String skuName;

    /**
     * 规格编号
     */
    @ExcelProperty(value = "规格编号", index = 3)
    private String skuCode;

    /**
     * 商品位置
     */
    @ExcelProperty(value = "商品位置", index = 4)
    private String locationName;

    /**
     * 所属仓库
     */
    @ExcelProperty(value = "所属仓库", index = 5)
    private String warehouseName;

    /**
     * 库存数量
     */
    @ExcelProperty(value = "库存数量", index = 6)
    private BigDecimal quantity;

    /**
     * 进价(元)
     */
    @ExcelProperty(value = "进价(元)", index = 7)
    private BigDecimal costPrice;

    /**
     * 售价(元)
     */
    @ExcelProperty(value = "售价(元)", index = 8)
    private BigDecimal sellingPrice;

    /**
     * 库存货值(进价) = 库存数量 × 进价
     */
    @ColumnWidth(18)
    @ExcelProperty(value = "库存货值(进价)", index = 9)
    private BigDecimal totalCost;

    /**
     * 库存货值(售价) = 库存数量 × 售价
     */
    @ColumnWidth(18)
    @ExcelProperty(value = "库存货值(售价)", index = 10)
    private BigDecimal totalSelling;

    /**
     * 备注
     */
    @ColumnWidth(20)
    @ExcelProperty(value = "备注", index = 11)
    private String remark;

}
