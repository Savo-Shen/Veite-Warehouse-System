package com.ruoyi.wms.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 会话（按用户隔离）。
 *
 * @author Savo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_ai_conversation")
public class AiConversation extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    /** 会话标题（取首条消息） */
    private String title;

    /** 所属用户ID */
    private Long userId;
}
