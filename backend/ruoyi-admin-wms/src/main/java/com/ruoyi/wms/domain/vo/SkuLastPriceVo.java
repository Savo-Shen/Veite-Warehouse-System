package com.ruoyi.wms.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 客户商品最近一次出库价格视图对象
 *
 * @author zcc
 */
@Data
public class SkuLastPriceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规格id
     */
    private Long skuId;

    /**
     * 上次出库数量
     */
    private BigDecimal quantity;

    /**
     * 上次出库金额
     */
    private BigDecimal amount;

    /**
     * 上次出库单价
     */
    private BigDecimal price;

    /**
     * 上次出库单号
     */
    private String orderNo;

    /**
     * 上次出库时间（录入时间）
     */
    private Date createTime;

    /**
     * 上次出库的业务日期，补录的单子按这个算「最近」
     */
    private LocalDate bizDate;
}
