package com.ruoyi.wms.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.ruoyi.common.excel.annotation.ExcelDictFormat;
import com.ruoyi.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.ruoyi.wms.domain.entity.Merchant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 往来单位视图对象 wms_merchant
 *
 * @author zcc
 * @date 2024-07-16
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Merchant.class)
public class MerchantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @ExcelProperty(value = "id")
    private Long id;

    /**
     * 编号
     */
    @ExcelProperty(value = "编号")
    private String merchantCode;

    /**
     * 名称
     */
    @ExcelProperty(value = "名称")
    private String merchantName;

    /**
     * 企业类型
     */
    @ExcelProperty(value = "企业类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "merchant_type")
    private Integer merchantType;

    /**
     * 级别
     */
    @ExcelProperty(value = "级别")
    private String merchantLevel;

    /**
     * 开户行
     */
    @ExcelProperty(value = "开户行")
    private String bankName;

    /**
     * 银行账户
     */
    @ExcelProperty(value = "银行账户")
    private String bankAccount;

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
     * 手机号
     */
    @ExcelProperty(value = "手机号")
    private String mobile;

    /**
     * 座机号
     */
    @ExcelProperty(value = "座机号")
    private String tel;

    /**
     * 联系人
     */
    @ExcelProperty(value = "联系人")
    private String contactPerson;

    /**
     * Email
     */
    @ExcelProperty(value = "Email")
    private String email;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 单位图片OSS ID，多个用逗号分隔
     */
    private String imageIds;


}
