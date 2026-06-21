package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.wms.service.DbAlignService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据库对齐工具：补齐旧库缺失的新版本表/字段/菜单
 *
 * @author savo
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/dbAlign")
public class DbAlignController extends BaseController {

    private final DbAlignService dbAlignService;

    /**
     * 检测数据库与最新版本的差异
     */
    @SaCheckPermission("wms:tool:dbAlign")
    @GetMapping("/check")
    public R<List<Map<String, Object>>> check() {
        return R.ok(dbAlignService.check());
    }

    /**
     * 一键对齐（仅补齐缺失项，幂等）
     */
    @SaCheckPermission("wms:tool:dbAlign")
    @Log(title = "数据库对齐", businessType = BusinessType.OTHER)
    @PostMapping("/run")
    public R<Map<String, Object>> run() {
        return R.ok(dbAlignService.align());
    }
}
