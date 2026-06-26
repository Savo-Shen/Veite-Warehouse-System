package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.wms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 仓库经营看板。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 首页/数据大屏经营总览。
     */
    @SaCheckPermission("wms:inventory:all")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(dashboardService.overview());
    }
}
