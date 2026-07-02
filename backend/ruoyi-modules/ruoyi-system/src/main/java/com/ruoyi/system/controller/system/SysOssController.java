package com.ruoyi.system.controller.system;


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.util.ObjectUtil;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.web.core.BaseController;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.common.core.validate.QueryGroup;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.system.domain.bo.SysOssBo;
import com.ruoyi.system.domain.entity.SysOssBlob;
import com.ruoyi.system.domain.vo.SysOssVo;
import com.ruoyi.system.service.SysOssService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 文件上传 控制层
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/oss")
public class SysOssController extends BaseController {

    private final SysOssService sysSssService;

    /**
     * 查询OSS对象存储列表
     */
    @SaCheckPermission(value = {"system:oss:list", "wms:receipt:all", "wms:shipment:all"}, mode = SaMode.OR)
    @GetMapping("/list")
    public TableDataInfo<SysOssVo> list(@Validated(QueryGroup.class) SysOssBo bo, PageQuery pageQuery) {
        return sysSssService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询OSS对象基于id串
     *
     * @param ossIds OSS对象ID串
     */
    @SaCheckPermission("system:oss:list")
    @GetMapping("/listByIds/{ossIds}")
    public R<List<SysOssVo>> listByIds(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ossIds) {
        List<SysOssVo> list = sysSssService.listByIds(Arrays.asList(ossIds));
        return R.ok(list);
    }

    /**
     * 上传OSS对象存储
     *
     * @param file 文件
     */
    @SaCheckPermission(value = {"system:oss:upload", "wms:receipt:all", "wms:shipment:all"}, mode = SaMode.OR)
    @Log(title = "OSS对象存储", businessType = BusinessType.INSERT)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        if (ObjectUtil.isNull(file)) {
            throw new ServiceException("上传文件不能为空");
        }
        SysOssVo oss = sysSssService.upload(file);
        return R.ok(Map.of(
            "url", oss.getUrl(),
            "fileName", oss.getOriginalName(),
            "ossId", oss.getOssId().toString()
        ));
    }

    /**
     * 下载OSS对象
     *
     * @param ossId OSS对象ID
     */
    @SaCheckPermission("system:oss:download")
    @GetMapping("/download/{ossId}")
    public void download(@PathVariable Long ossId, HttpServletResponse response) throws IOException {
        sysSssService.download(ossId,response);
    }

    /**
     * 读取数据库存储模式下的文件内容（公开访问，供 &lt;img&gt; 等直接加载）
     * 该路径已在 application.yml 的 security.excludes 中放行匿名访问
     *
     * @param ossId OSS对象ID
     */
    @GetMapping("/blob/{ossId}")
    public void blob(@PathVariable Long ossId, HttpServletResponse response) throws IOException {
        SysOssBlob blob = sysSssService.getBlob(ossId);
        if (blob == null || blob.getData() == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (blob.getContentType() != null) {
            response.setContentType(blob.getContentType());
        } else {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        // 图片等静态资源允许缓存，减少重复请求
        response.setHeader("Cache-Control", "max-age=31536000");
        response.setContentLength(blob.getData().length);
        response.getOutputStream().write(blob.getData());
    }

    /**
     * 删除OSS对象存储
     *
     * @param ossIds OSS对象ID串
     */
    @SaCheckPermission(value = {"system:oss:remove", "wms:receipt:all", "wms:shipment:all"}, mode = SaMode.OR)
    @Log(title = "OSS对象存储", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ossIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ossIds) {
        return toAjax(sysSssService.deleteWithValidByIds(List.of(ossIds), true));
    }

}
