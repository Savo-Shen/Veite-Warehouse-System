package com.ruoyi.wms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.wms.service.DbBackupService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/wms/dbBackup")
public class DbBackupController extends BaseController {

    private final DbBackupService dbBackupService;

    @SaCheckPermission("wms:tool:dbBackup")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(dbBackupService.list());
    }

    @SaCheckPermission("wms:tool:dbBackup")
    @Log(title = "数据库备份", businessType = BusinessType.OTHER)
    @PostMapping("/create")
    public R<Map<String, Object>> create() {
        return R.ok(dbBackupService.create());
    }

    @SaCheckPermission("wms:tool:dbBackup")
    @GetMapping("/download/{filename:.+}")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        Path path = dbBackupService.resolve(filename);
        Resource resource = new FileSystemResource(path);
        String disposition = "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .contentLength(path.toFile().length())
            .body(resource);
    }

    @SaCheckPermission("wms:tool:dbBackup")
    @Log(title = "数据库备份", businessType = BusinessType.DELETE)
    @DeleteMapping("/{filename:.+}")
    public R<Void> delete(@PathVariable String filename) {
        dbBackupService.delete(filename);
        return R.ok();
    }
}
