package com.ruoyi.wms.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 平台类目 ID 回填入参。
 * <p>
 * 只开放三个平台的类目 ID 与路径。不要直接把 EcCategory 实体当入参——那样调用方
 * 能顺手改掉 ec_code / ec_l1 / ec_l2 / order_num，属于典型的 mass assignment。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class EcCategoryPlatformBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "类目不能为空")
    private Long id;

    @Size(max = 64, message = "拼多多类目ID 过长")
    private String pddCatId;
    @Size(max = 255, message = "拼多多类目路径过长")
    private String pddCatPath;

    @Size(max = 64, message = "淘宝类目ID 过长")
    private String tbCatId;
    @Size(max = 255, message = "淘宝类目路径过长")
    private String tbCatPath;

    @Size(max = 64, message = "抖音类目ID 过长")
    private String dyCatId;
    @Size(max = 255, message = "抖音类目路径过长")
    private String dyCatPath;
}
