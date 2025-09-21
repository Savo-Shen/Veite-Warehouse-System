package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.validate.AddGroup;
import com.ruoyi.common.core.validate.EditGroup;
import com.ruoyi.common.excel.utils.ExcelUtil;
import com.ruoyi.common.idempotent.annotation.RepeatSubmit;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.common.web.core.BaseController;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.wms.domain.bo.LocationBo;
import com.ruoyi.wms.domain.vo.LocationVo;
import com.ruoyi.wms.service.LocationService;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/location")
public class LocationController extends BaseController {

    private final LocationService locationService;

    /**
     * 查询物料列表
     */
    @GetMapping("/list")
    @SaCheckPermission("wms:location:list")
    public TableDataInfo<LocationVo> list(LocationBo bo, PageQuery pageQuery) {
        return locationService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询物料列表
     */
    @GetMapping("/listNoPage")
    @SaCheckPermission("wms:location:list")
    public R<List<LocationVo>> list(LocationBo bo) {
        return R.ok(locationService.queryList(bo));
    }

    /**
     * 导出物料列表
     */
    @Log(title = "物料", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @SaCheckPermission("wms:location:list")
    public void export(LocationBo bo, HttpServletResponse response) {
        List<LocationVo> list = locationService.queryList(bo);
        ExcelUtil.exportExcel(list, "物料", LocationVo.class, response);
    }

    /**
     * 获取物料详细信息
     *
     * @param id 主键
     */
    @GetMapping("/{id}")
    @SaCheckPermission("wms:location:list")
    public R<LocationVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(locationService.queryById(id));
    }

    /**
     * 新增物料
     */
    @Log(title = "物料", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    @SaCheckPermission("wms:location:edit")
    public R<Void> add(@Validated(AddGroup.class) @RequestBody LocationBo form) {
        locationService.insertByForm(form);
        return R.ok();
    }
    /**
     * 修改物料
     */
    @Log(title = "物料", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    @SaCheckPermission("wms:location:edit")
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody LocationBo form) {
        locationService.updateByForm(form);
        return R.ok();
    }

    /**
     * 删除物料
     *
     * @param id 主键
     */
    @Log(title = "物料", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    @SaCheckPermission("wms:location:edit")
    public R<Void> remove(@NotNull(message = "主键不能为空")
                          @PathVariable Long id) {
        locationService.deleteById(id);
        return R.ok();
    }
}
