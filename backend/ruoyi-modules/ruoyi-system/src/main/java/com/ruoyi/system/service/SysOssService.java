package com.ruoyi.system.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.io.FileUtil;
import com.ruoyi.common.core.constant.CacheNames;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.common.core.service.ConfigService;
import com.ruoyi.common.core.service.OssService;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.MapstructUtils;
import com.ruoyi.common.core.utils.StreamUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.utils.file.FileUtils;
import com.ruoyi.common.core.utils.SpringUtils;
import com.ruoyi.common.oss.core.OssClient;
import com.ruoyi.common.oss.entity.UploadResult;
import com.ruoyi.common.oss.enumd.AccessPolicyType;
import com.ruoyi.common.oss.factory.OssFactory;
import com.ruoyi.system.domain.entity.SysOss;
import com.ruoyi.system.domain.entity.SysOssBlob;
import com.ruoyi.system.domain.bo.SysOssBo;
import com.ruoyi.system.domain.vo.SysOssVo;
import com.ruoyi.system.mapper.SysOssBlobMapper;
import com.ruoyi.system.mapper.SysOssMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文件上传 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysOssService implements OssService {

    /**
     * 数据库存储模式下 sys_oss.service 的取值，用于区分对象存储服务商
     */
    private static final String DB_SERVICE = "db";
    /**
     * 数据库存储模式下文件回读端点前缀（配合前端 VITE_APP_BASE_API 拼接）
     */
    private static final String DB_URL_PREFIX = "/system/oss/blob/";
    /**
     * 文件存储方式参数：db=存数据库，其它=走对象存储
     */
    private static final String STORAGE_MODE_KEY = "sys.oss.storageMode";

    private final SysOssMapper ossMapper;
    private final SysOssBlobMapper ossBlobMapper;
    private final ConfigService configService;

    /**
     * 是否启用数据库存储模式
     */
    private boolean isDbStorage() {
        return DB_SERVICE.equalsIgnoreCase(configService.getConfigValue(STORAGE_MODE_KEY));
    }

    public TableDataInfo<SysOssVo> queryPageList(SysOssBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysOss> lqw = buildQueryWrapper(bo);
        Page<SysOssVo> result = ossMapper.selectVoPage(pageQuery.build(), lqw);
        List<SysOssVo> filterResult = StreamUtils.toList(result.getRecords(), this::matchingUrl);
        result.setRecords(filterResult);
        return TableDataInfo.build(result);
    }

    public List<SysOssVo> listByIds(Collection<Long> ossIds) {
        List<SysOssVo> list = new ArrayList<>();
        for (Long id : ossIds) {
            SysOssVo vo = SpringUtils.getAopProxy(this).getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                try {
                    list.add(this.matchingUrl(vo));
                } catch (Exception ignored) {
                    // 如果oss异常无法连接则将数据直接返回
                    list.add(vo);
                }            }
        }
        return list;
    }

    public String selectUrlByIds(String ossIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(ossIds, Convert::toLong)) {
            SysOssVo vo = SpringUtils.getAopProxy(this).getById(id);
            if (ObjectUtil.isNotNull(vo)) {
                try {
                    list.add(this.matchingUrl(vo).getUrl());
                } catch (Exception ignored) {
                    // 如果oss异常无法连接则将数据直接返回
                    list.add(vo.getUrl());
                }
            }
        }
        return String.join(StringUtils.SEPARATOR, list);
    }

    private LambdaQueryWrapper<SysOss> buildQueryWrapper(SysOssBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<SysOss> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getFileName()), SysOss::getFileName, bo.getFileName());
        lqw.like(StringUtils.isNotBlank(bo.getOriginalName()), SysOss::getOriginalName, bo.getOriginalName());
        lqw.eq(StringUtils.isNotBlank(bo.getFileSuffix()), SysOss::getFileSuffix, bo.getFileSuffix());
        lqw.eq(StringUtils.isNotBlank(bo.getUrl()), SysOss::getUrl, bo.getUrl());
        lqw.between(params.get("beginCreateTime") != null && params.get("endCreateTime") != null,
            SysOss::getCreateTime, params.get("beginCreateTime"), params.get("endCreateTime"));
        lqw.eq(StringUtils.isNotBlank(bo.getCreateBy()), SysOss::getCreateBy, bo.getCreateBy());
        lqw.eq(StringUtils.isNotBlank(bo.getService()), SysOss::getService, bo.getService());
        return lqw;
    }

    @Cacheable(cacheNames = CacheNames.SYS_OSS, key = "#ossId")
    public SysOssVo getById(Long ossId) {
        return ossMapper.selectVoById(ossId);
    }

    public void download(Long ossId, HttpServletResponse response) throws IOException {
        SysOssVo sysOss = SpringUtils.getAopProxy(this).getById(ossId);
        if (ObjectUtil.isNull(sysOss)) {
            throw new ServiceException("文件数据不存在!");
        }
        FileUtils.setAttachmentResponseHeader(response, sysOss.getOriginalName());
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE + "; charset=UTF-8");
        if (DB_SERVICE.equals(sysOss.getService())) {
            SysOssBlob blob = getBlob(ossId);
            if (blob == null || blob.getData() == null) {
                throw new ServiceException("文件数据不存在!");
            }
            try {
                response.getOutputStream().write(blob.getData());
                response.setContentLength(blob.getData().length);
            } catch (IOException e) {
                throw new ServiceException(e.getMessage());
            }
            return;
        }
        OssClient storage = OssFactory.instance(sysOss.getService());
        try(InputStream inputStream = storage.getObjectContent(sysOss.getUrl())) {
            int available = inputStream.available();
            IoUtil.copy(inputStream, response.getOutputStream(), available);
            response.setContentLength(available);
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    public SysOssVo upload(MultipartFile file) {
        String originalfileName = file.getOriginalFilename();
        String suffix = StringUtils.substring(originalfileName, originalfileName.lastIndexOf("."), originalfileName.length());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException(e.getMessage());
        }
        if (isDbStorage()) {
            return uploadToDb(bytes, originalfileName, suffix, file.getContentType());
        }
        OssClient storage = OssFactory.instance();
        UploadResult uploadResult = storage.uploadSuffix(bytes, suffix, file.getContentType());
        // 保存文件信息
        return buildResultEntity(originalfileName, suffix, storage.getConfigKey(), uploadResult);
    }

    public SysOssVo upload(File file) {
        String originalfileName = file.getName();
        String suffix = StringUtils.substring(originalfileName, originalfileName.lastIndexOf("."), originalfileName.length());
        if (isDbStorage()) {
            return uploadToDb(FileUtil.readBytes(file), originalfileName, suffix, FileUtil.getMimeType(file.getName()));
        }
        OssClient storage = OssFactory.instance();
        UploadResult uploadResult = storage.uploadSuffix(file, suffix);
        // 保存文件信息
        return buildResultEntity(originalfileName, suffix, storage.getConfigKey(), uploadResult);
    }

    private SysOssVo buildResultEntity(String originalfileName, String suffix, String configKey, UploadResult uploadResult) {
        SysOss oss = new SysOss();
        oss.setUrl(uploadResult.getUrl());
        oss.setFileSuffix(suffix);
        oss.setFileName(uploadResult.getFilename());
        oss.setOriginalName(originalfileName);
        oss.setService(configKey);
        ossMapper.insert(oss);
        SysOssVo sysOssVo = MapstructUtils.convert(oss, SysOssVo.class);
        return this.matchingUrl(sysOssVo);
    }

    /**
     * 数据库存储模式：文件二进制写入 sys_oss_blob，
     * sys_oss.url 记为 /system/oss/blob/{ossId}，由公开端点回读
     */
    private SysOssVo uploadToDb(byte[] bytes, String originalfileName, String suffix, String contentType) {
        SysOss oss = new SysOss();
        oss.setFileSuffix(suffix);
        oss.setFileName(originalfileName);
        oss.setOriginalName(originalfileName);
        oss.setService(DB_SERVICE);
        // 先插入以获取雪花主键，再回填 url
        oss.setUrl(DB_URL_PREFIX + "pending");
        ossMapper.insert(oss);
        oss.setUrl(DB_URL_PREFIX + oss.getOssId());
        ossMapper.updateById(oss);

        SysOssBlob blob = new SysOssBlob();
        blob.setOssId(oss.getOssId());
        blob.setContentType(contentType);
        blob.setData(bytes);
        ossBlobMapper.insert(blob);

        return MapstructUtils.convert(oss, SysOssVo.class);
    }

    /**
     * 读取数据库存储模式下的文件内容
     */
    public SysOssBlob getBlob(Long ossId) {
        return ossBlobMapper.selectById(ossId);
    }

    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 做一些业务上的校验,判断是否需要校验
        }
        List<SysOss> list = ossMapper.selectBatchIds(ids);
        for (SysOss sysOss : list) {
            if (DB_SERVICE.equals(sysOss.getService())) {
                // 数据库存储模式：删除对应二进制内容
                ossBlobMapper.deleteById(sysOss.getOssId());
                continue;
            }
            OssClient storage = OssFactory.instance(sysOss.getService());
            storage.delete(sysOss.getUrl());
        }
        return ossMapper.deleteBatchIds(ids) > 0;
    }

    /**
     * 匹配Url
     *
     * @param oss OSS对象
     * @return oss 匹配Url的OSS对象
     */
    private SysOssVo matchingUrl(SysOssVo oss) {
        // 数据库存储模式：url 已是回读端点，直接返回
        if (DB_SERVICE.equals(oss.getService())) {
            return oss;
        }
        OssClient storage = OssFactory.instance(oss.getService());
        // 仅修改桶类型为 private 的URL，临时URL时长为120s
        if (AccessPolicyType.PRIVATE == storage.getAccessPolicy()) {
            oss.setUrl(storage.getPrivateUrl(oss.getFileName(), 120));
        }
        return oss;
    }
}
