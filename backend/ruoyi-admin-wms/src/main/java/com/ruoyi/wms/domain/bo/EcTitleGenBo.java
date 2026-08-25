package com.ruoyi.wms.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * AI 生成电商标题的入参
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class EcTitleGenBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 要生成标题的电商商品 ID，批量生成时可传多个。
     * 上限 200：AI 调用按次计费，且实测约 2 秒/个，200 个已需 7 分钟左右。
     */
    @NotEmpty(message = "请至少选择一个商品")
    @Size(max = 200, message = "单次最多生成 200 个商品的标题")
    private List<Long> productIds;

    /** 是否覆盖已有标题；false 时跳过已写好标题的商品 */
    private Boolean overwrite = false;

    /** 额外的风格要求，会拼进提示词，例如「突出耐高温」「面向机床维修客户」 */
    private String extraHint;
}
