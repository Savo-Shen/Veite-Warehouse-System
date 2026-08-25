package com.ruoyi.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SKU 规格参数对象 ec_sku_spec
 * <p>
 * 由 SKU 型号解析 + 厂商公开样本的标准件通用规格生成，非实测。
 * 不含重量与包装尺寸——那两项直接决定运费，必须实测，存在 wms_item_sku 上。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ec_sku_spec")
public class EcSkuSpec extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "sku_id")
    private Long skuId;

    /** 规格参数 JSON，以字符串承载 */
    private String spec;

    /** high=型号完整解析 / medium=部分 / low=仅类目通用 */
    private String confidence;

    private String specSource;
}
