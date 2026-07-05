package com.ruoyi.wms.domain.bo;

import com.ruoyi.common.core.validate.AddGroup;
import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import com.ruoyi.wms.domain.entity.Inventory;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存业务对象 wms_inventory
 *
 * @author zcc
 * @date 2024-07-19
 */

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Inventory.class, reverseConvertGenerate = false)
public class InventoryBo extends BaseEntity {

    /**
     *
     */
    @NotNull(message = "不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 规格ID
     */
    @NotNull(message = "规格ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long skuId;

    /**
     * 所属仓库
     */
    @NotNull(message = "所属仓库不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long warehouseId;

    /**
     * 库存
     */
    @NotNull(message = "库存不能为空", groups = { AddGroup.class, EditGroup.class })
    private BigDecimal quantity;

    /**
     * 备注
     */
    private String remark;

    /**
     * 最小数量（库存 >= 该值）
     */
    private BigDecimal minQuantity;

    /**
     * 最大数量（库存 <= 该值，用于筛选缺货/低库存商品）
     */
    private BigDecimal maxQuantity;

    private String itemName;
    private String itemCode;
    private String skuName;
    private String skuCode;
    private Long itemLocationId;
    private Long itemId;
    private Long itemCategory;

    /**
     * 商品标签ID（按标签筛选）
     */
    private Long tagId;

    /**
     * 排序方式：默认为空（按商品/仓库分组）；quantityAsc 库存升序；quantityDesc 库存降序
     */
    private String sortMode;
}
