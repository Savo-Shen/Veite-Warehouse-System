package com.ruoyi.wms.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 消息（属于某个会话）。
 *
 * @author Savo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_ai_message")
public class AiMessage extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** 所属会话ID */
    private Long conversationId;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 工具调用轨迹(JSON) */
    private String toolTrace;

    /** 建单草稿(JSON) */
    private String draft;

    /** 本次回复耗时(毫秒) */
    private Long elapsedMs;
}
