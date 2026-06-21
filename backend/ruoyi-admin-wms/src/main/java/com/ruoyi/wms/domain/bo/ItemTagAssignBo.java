package com.ruoyi.wms.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量打标签业务对象
 *
 * @author savo
 * @date 2026-06-20
 */
@Data
public class ItemTagAssignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品ID集合
     */
    @NotEmpty(message = "商品不能为空")
    private List<Long> itemIds;

    /**
     * 标签ID集合
     */
    @NotEmpty(message = "标签不能为空")
    private List<Long> tagIds;

}
