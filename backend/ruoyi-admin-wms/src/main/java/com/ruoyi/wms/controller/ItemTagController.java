package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.validate.AddGroup;
import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.common.idempotent.annotation.RepeatSubmit;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.wms.domain.bo.ItemTagAssignBo;
import com.ruoyi.wms.domain.bo.ItemTagBo;
import com.ruoyi.wms.domain.vo.ItemTagVo;
import com.ruoyi.wms.service.ItemTagService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品标签
 *
 * @author savo
 * @date 2026-06-20
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/itemTag")
public class ItemTagController extends BaseController {

    private final ItemTagService itemTagService;

    /**
     * 查询商品标签列表
     */
    @SaCheckPermission("wms:itemTag:list")
    @GetMapping("/list")
    public TableDataInfo<ItemTagVo> list(ItemTagBo bo, PageQuery pageQuery) {
        return itemTagService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询商品标签列表（不分页）
     */
    @SaCheckPermission("wms:itemTag:list")
    @GetMapping("/listNoPage")
    public R<List<ItemTagVo>> listNoPage(ItemTagBo bo) {
        return R.ok(itemTagService.queryList(bo));
    }

    /**
     * 获取商品标签详细信息
     */
    @SaCheckPermission("wms:itemTag:list")
    @GetMapping("/{id}")
    public R<ItemTagVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return R.ok(itemTagService.queryById(id));
    }

    /**
     * 新增商品标签
     */
    @SaCheckPermission("wms:itemTag:edit")
    @Log(title = "商品标签", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ItemTagBo bo) {
        itemTagService.insertByBo(bo);
        return R.ok();
    }

    /**
     * 修改商品标签
     */
    @SaCheckPermission("wms:itemTag:edit")
    @Log(title = "商品标签", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ItemTagBo bo) {
        itemTagService.updateByBo(bo);
        return R.ok();
    }

    /**
     * 删除商品标签
     */
    @SaCheckPermission("wms:itemTag:edit")
    @Log(title = "商品标签", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        itemTagService.deleteById(id);
        return R.ok();
    }

    /**
     * 批量给商品打标签
     */
    @SaCheckPermission("wms:itemTag:edit")
    @Log(title = "商品标签", businessType = BusinessType.UPDATE)
    @PostMapping("/assign")
    public R<Void> assign(@Validated @RequestBody ItemTagAssignBo bo) {
        itemTagService.assignTags(bo);
        return R.ok();
    }

    /**
     * 移除某个商品的某个标签
     */
    @SaCheckPermission("wms:itemTag:edit")
    @Log(title = "商品标签", businessType = BusinessType.DELETE)
    @DeleteMapping("/rel/{itemId}/{tagId}")
    public R<Void> removeRel(@PathVariable Long itemId, @PathVariable Long tagId) {
        itemTagService.removeRel(itemId, tagId);
        return R.ok();
    }
}
