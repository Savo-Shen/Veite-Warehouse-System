package com.ruoyi.wms.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.satoken.utils.LoginHelper;
import com.ruoyi.wms.ai.tool.DraftSupport;
import com.ruoyi.wms.ai.domain.AiConversation;
import com.ruoyi.wms.ai.domain.AiMessage;
import com.ruoyi.wms.ai.mapper.AiConversationMapper;
import com.ruoyi.wms.ai.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 会话/消息持久化，全部按当前登录用户隔离。
 *
 * @author Savo
 */
@Service
@RequiredArgsConstructor
public class AiConversationService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final DraftSupport draftSupport;

    /** 作为大模型上下文回放的最大历史消息条数 */
    private static final int HISTORY_LIMIT = 16;

    /** 单条历史消息回放给模型的最大字符数（用户贴的长清单、超长回复都截断，别撑爆上下文） */
    private static final int HISTORY_MAX_CHARS = 6000;

    /** 上一轮草稿摘要回放的最大字符数 */
    private static final int DRAFT_SUMMARY_MAX_CHARS = 3000;

    /** 我的会话列表（按更新时间倒序） */
    public List<AiConversation> listMine() {
        return conversationMapper.selectList(Wrappers.<AiConversation>lambdaQuery()
            .eq(AiConversation::getUserId, LoginHelper.getUserId())
            .orderByDesc(AiConversation::getUpdateTime)
            .orderByDesc(AiConversation::getId));
    }

    /** 取已有会话（校验归属）或新建一个（当前线程取用户） */
    public AiConversation getOrCreate(Long conversationId, String firstMessage) {
        return getOrCreate(conversationId, firstMessage, LoginHelper.getUserId());
    }

    /** 取已有会话（校验归属）或新建一个（显式传入用户，供异步线程使用） */
    public AiConversation getOrCreate(Long conversationId, String firstMessage, Long userId) {
        if (conversationId != null) {
            return requireOwned(conversationId, userId);
        }
        AiConversation c = new AiConversation();
        c.setUserId(userId);
        c.setTitle(buildTitle(firstMessage));
        conversationMapper.insert(c);
        return c;
    }

    /**
     * 加载会话最近若干条消息，转成大模型上下文。
     * <p>
     * 助手消息如果带了草稿，把草稿的精简摘要（哪些商品、数量、单价、skuId）一并回放，
     * 这样用户接着说“把第二个改成 3 卷”“再加一个接头”时模型知道上一张草稿里有什么。
     */
    public List<java.util.Map<String, Object>> loadHistory(Long conversationId) {
        List<AiMessage> recent = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
            .eq(AiMessage::getConversationId, conversationId)
            .orderByDesc(AiMessage::getId)
            .last("limit " + HISTORY_LIMIT));
        List<java.util.Map<String, Object>> history = new ArrayList<>();
        // selectList 是倒序，反转回正序
        for (int i = recent.size() - 1; i >= 0; i--) {
            AiMessage m = recent.get(i);
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            String content = truncate(m.getContent(), HISTORY_MAX_CHARS);
            if ("assistant".equals(m.getRole()) && m.getDraft() != null && !m.getDraft().isBlank()) {
                String summary = draftSupport.summarizeGeneric(m.getDraft());
                if (summary != null && !summary.equals(m.getDraft())) {
                    content += "\n\n[本轮生成的草稿摘要，供后续修改参考]\n" + truncate(summary, DRAFT_SUMMARY_MAX_CHARS);
                }
            }
            history.add(java.util.Map.of("role", m.getRole(), "content", content));
        }
        return history;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…（已截断，原文共 " + s.length() + " 字）";
    }

    /** 追加一条消息，并刷新会话更新时间；返回消息 id */
    @Transactional
    public Long appendMessage(Long conversationId, String role, String content,
                              String toolTrace, String draft, Long elapsedMs) {
        AiMessage m = new AiMessage();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setToolTrace(toolTrace);
        m.setDraft(draft);
        m.setElapsedMs(elapsedMs);
        messageMapper.insert(m);

        AiConversation update = new AiConversation();
        update.setId(conversationId);
        update.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(update);
        return m.getId();
    }

    /** 覆盖某条消息的草稿 JSON（动作执行后回写 executed 标记） */
    public void updateDraft(Long messageId, String draftJson) {
        AiMessage upd = new AiMessage();
        upd.setId(messageId);
        upd.setDraft(draftJson);
        messageMapper.updateById(upd);
    }

    /** 取一条消息（不校验归属，调用方自己校验会话归属） */
    public AiMessage getMessage(Long messageId) {
        return messageMapper.selectById(messageId);
    }

    /** 某会话的全部消息（校验归属，正序） */
    public List<AiMessage> listMessages(Long conversationId) {
        requireOwned(conversationId);
        return messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
            .eq(AiMessage::getConversationId, conversationId)
            .orderByAsc(AiMessage::getId));
    }

    /** 删除我的会话及其消息 */
    @Transactional
    public void deleteMine(Long conversationId) {
        requireOwned(conversationId);
        messageMapper.delete(Wrappers.<AiMessage>lambdaQuery()
            .eq(AiMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    /** 校验会话存在且属于当前用户，否则抛异常 */
    private AiConversation requireOwned(Long conversationId) {
        return requireOwned(conversationId, LoginHelper.getUserId());
    }

    public AiConversation requireOwned(Long conversationId, Long userId) {
        AiConversation c = conversationMapper.selectById(conversationId);
        if (c == null) {
            throw new ServiceException("会话不存在");
        }
        if (!c.getUserId().equals(userId)) {
            throw new ServiceException("无权访问该会话");
        }
        return c;
    }

    private String buildTitle(String message) {
        if (message == null) {
            return "新对话";
        }
        String t = message.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return "新对话";
        }
        return t.length() > 20 ? t.substring(0, 20) + "…" : t;
    }
}
