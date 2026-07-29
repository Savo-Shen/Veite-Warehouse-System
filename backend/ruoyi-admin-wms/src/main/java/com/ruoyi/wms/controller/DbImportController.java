package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.wms.service.DbImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/dbImport")
public class DbImportController extends BaseController {

    private final DbImportService dbImportService;

    @SaCheckPermission("wms:tool:dbImport")
    @Log(title = "数据库导入", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) {
        return R.ok(dbImportService.upload(file));
    }

    @SaCheckPermission("wms:tool:dbImport")
    @GetMapping("/{sessionId}/preview")
    public R<Map<String, Object>> preview(@PathVariable String sessionId) {
        return R.ok(dbImportService.preview(sessionId));
    }

    @SaCheckPermission("wms:tool:dbImport")
    @Log(title = "数据库导入", businessType = BusinessType.IMPORT)
    @PostMapping("/{sessionId}/apply")
    public R<Map<String, Object>> apply(@PathVariable String sessionId, @RequestBody Map<String, Object> request) {
        return R.ok(dbImportService.apply(sessionId, request));
    }

    @SaCheckPermission("wms:tool:dbImport")
    @DeleteMapping("/{sessionId}")
    public R<Void> cancel(@PathVariable String sessionId) {
        dbImportService.cancel(sessionId);
        return R.ok();
    }
}
