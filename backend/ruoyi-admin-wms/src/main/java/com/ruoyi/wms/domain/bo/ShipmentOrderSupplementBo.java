package com.ruoyi.wms.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 出库单「事后补充」业务对象
 *
 * 出库单一旦出库/作废就锁死不给改——库存已经动过，改明细就对不上账。但备注和现场照片
 * 不碰库存也不碰金额：签收单是第二天才拍到的、当时漏写的一句说明，回头都得补得进去。
 * 所以单开这条口子，只让改这两样，其余字段一概不接。
 *
 * @author Savo Shen
 */
@Data
public class ShipmentOrderSupplementBo {

    /**
     * 出库单id
     */
    @NotNull(message = "出库单id不能为空")
    private Long id;

    /**
     * 备注。传空字符串就是清空
     */
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    /**
     * 补充图片 OSS ID，多个用逗号分隔
     */
    @Size(max = 1000, message = "补充图片过多，请适当删减")
    private String supplementImageIds;
}
