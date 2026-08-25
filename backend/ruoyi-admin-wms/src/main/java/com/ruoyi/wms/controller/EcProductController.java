package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.wms.domain.bo.EcCategoryPlatformBo;
import com.ruoyi.wms.domain.bo.EcProductBo;
import com.ruoyi.wms.domain.bo.EcSkuMeasureBo;
import com.ruoyi.wms.domain.bo.EcTitleGenBo;
import com.ruoyi.wms.domain.vo.EcCategoryVo;
import com.ruoyi.wms.domain.vo.EcGenTaskVo;
import com.ruoyi.wms.domain.vo.EcListingSkuVo;
import com.ruoyi.wms.domain.vo.EcProductVo;
import com.ruoyi.wms.service.EcProductService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 电商上新
 *
 * @author savo
 * @date 2026-08-23
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/ecProduct")
public class EcProductController extends BaseController {

    private final EcProductService ecProductService;

    /**
     * 上新列表
     */
    @SaCheckPermission("wms:ecProduct:list")
    @GetMapping("/list")
    public TableDataInfo<EcProductVo> list(EcProductBo bo, PageQuery pageQuery) {
        return ecProductService.queryPageList(bo, pageQuery);
    }

    /**
     * 某个电商商品下的 SKU 明细
     */
    @SaCheckPermission("wms:ecProduct:list")
    @GetMapping("/skus/{productId}")
    public R<List<EcListingSkuVo>> skus(@NotNull(message = "主键不能为空") @PathVariable Long productId) {
        return R.ok(ecProductService.querySkus(productId));
    }

    /**
     * 电商类目（含商品计数与三个平台的类目 ID）
     */
    @SaCheckPermission("wms:ecProduct:list")
    @GetMapping("/categories")
    public R<List<EcCategoryVo>> categories() {
        return R.ok(ecProductService.queryCategories());
    }

    /**
     * 保存标题、卖点、备注
     */
    @SaCheckPermission("wms:ecProduct:edit")
    @Log(title = "电商上新", businessType = BusinessType.UPDATE)
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EcProductBo bo) {
        ecProductService.updateByBo(bo);
        return R.ok();
    }

    /**
     * 标记上架状态
     */
    @SaCheckPermission("wms:ecProduct:edit")
    @Log(title = "电商上新", businessType = BusinessType.UPDATE)
    @PutMapping("/status/{id}")
    public R<Void> status(@PathVariable Long id, @RequestParam String status) {
        ecProductService.updateStatus(id, status);
        return R.ok();
    }

    /**
     * 回填平台类目 ID（拼多多/淘宝/抖音）
     */
    @SaCheckPermission("wms:ecProduct:edit")
    @Log(title = "电商上新", businessType = BusinessType.UPDATE)
    @PutMapping("/category")
    public R<Void> category(@Validated @RequestBody EcCategoryPlatformBo bo) {
        ecProductService.updateCategoryPlatformId(bo);
        return R.ok();
    }

    /**
     * 录入实测数据：重量与包装尺寸（顺带改价）
     */
    @SaCheckPermission("wms:ecProduct:edit")
    @Log(title = "电商上新", businessType = BusinessType.UPDATE)
    @PutMapping("/measures")
    public R<Integer> measures(@Validated @RequestBody List<EcSkuMeasureBo> list) {
        return R.ok(ecProductService.saveMeasures(list));
    }

    /**
     * 提交 AI 生成标题任务（单个或批量），立即返回 taskId。
     * 实测约 2 秒/个，批量必须异步，否则撞 HTTP 超时且中断后无从得知进度。
     */
    @SaCheckPermission("wms:ecProduct:ai")
    @Log(title = "电商上新-AI生成标题", businessType = BusinessType.UPDATE)
    @PostMapping("/genTitle")
    public R<EcGenTaskVo> genTitle(@Validated @RequestBody EcTitleGenBo bo) {
        return R.ok(ecProductService.submitGenTitles(bo));
    }

    /**
     * 查询生成任务进度
     */
    @SaCheckPermission("wms:ecProduct:ai")
    @GetMapping("/genTitle/{taskId}")
    public R<EcGenTaskVo> genTitleProgress(@PathVariable String taskId) {
        return R.ok(ecProductService.queryGenTask(taskId));
    }
}
