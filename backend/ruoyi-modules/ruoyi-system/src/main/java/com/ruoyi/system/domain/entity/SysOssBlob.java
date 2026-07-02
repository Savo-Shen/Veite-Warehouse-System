package com.ruoyi.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * OSS 文件二进制内容（数据库存储模式）
 * 与 {@link SysOss} 通过 ossId 一一对应
 *
 * @author claude
 */
@Data
@TableName("sys_oss_blob")
public class SysOssBlob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象存储主键（与 sys_oss.oss_id 一致）
     */
    @TableId(value = "oss_id")
    private Long ossId;

    /**
     * MIME 类型
     */
    private String contentType;

    /**
     * 文件二进制内容
     */
    private byte[] data;

}
