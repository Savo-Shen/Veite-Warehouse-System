package com.ruoyi.wms.ai;

import cn.hutool.core.thread.ThreadUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.satoken.utils.LoginHelper;
import com.ruoyi.wms.ai.domain.AiConversation;
import com.ruoyi.wms.ai.domain.AiMessage;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 助手接口。运行在登录态下，工具经由现有 Service 调用，会话/消息按用户隔离。
 *
 * @author Savo
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/wms/ai")
@RequiredArgsConstructor
public class AiAgentController {

    private final AgentOrchestrator agentOrchestrator;
    private final AiConversationService conversationService;
    private final ObjectMapper objectMapper;

    /**
     * 对话：在指定会话内继续（无 conversationId 则新建），自动持久化消息与历史上下文。
     */
    @PostMapping("/chat")
    public R<ChatResponse> chat(@Validated @RequestBody ChatRequest request) {
        AiConversation conv = conversationService.getOrCreate(request.getConversationId(), request.getMessage());
        // 先取历史上下文（不含本轮），再落用户消息
        List<java.util.Map<String, Object>> history = conversationService.loadHistory(conv.getId());
        conversationService.appendMessage(conv.getId(), "user", request.getMessage(), null, null, null);

        long t0 = System.currentTimeMillis();
        AgentOrchestrator.AgentResult result = agentOrchestrator.chat(request.getMessage(), history);
        long elapsedMs = System.currentTimeMillis() - t0;

        String toolTraceJson = toJson(result.toolTrace());
        String draftJson = result.draft() == null ? null : toJson(result.draft());
        conversationService.appendMessage(conv.getId(), "assistant", result.reply(), toolTraceJson, draftJson, elapsedMs);

        return R.ok(new ChatResponse(result.reply(), result.toolTrace(), result.draft(), conv.getId(), elapsedMs));
    }

    /**
     * 流式对话（SSE）：实时推送工具状态与回复文本。
     * 事件：meta(会话ID) / status(工具状态) / delta(文本增量) / done(完整结果) / error。
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@Validated @RequestBody ChatRequest request) {
        // 在请求线程取用户（sa-token 是 ThreadLocal，异步线程取不到），传入工作线程
        Long userId = LoginHelper.getUserId();
        SseEmitter emitter = new SseEmitter(180_000L);
        ThreadUtil.execute(() -> streamChat(request, userId, emitter));
        return emitter;
    }

    private void streamChat(ChatRequest request, Long userId, SseEmitter emitter) {
        try {
            AiConversation conv = conversationService.getOrCreate(request.getConversationId(), request.getMessage(), userId);
            emitter.send(SseEmitter.event().name("meta").data(Map.of("conversationId", conv.getId())));

            List<Map<String, Object>> history = conversationService.loadHistory(conv.getId());
            conversationService.appendMessage(conv.getId(), "user", request.getMessage(), null, null, null);

            long t0 = System.currentTimeMillis();
            AgentOrchestrator.AgentResult result = agentOrchestrator.chatStream(
                request.getMessage(), history,
                new AgentOrchestrator.StreamSink() {
                    @Override
                    public void onStatus(String status) {
                        sendQuiet(emitter, "status", status);
                    }

                    @Override
                    public void onDelta(String delta) {
                        sendQuiet(emitter, "delta", delta);
                    }
                });
            long elapsedMs = System.currentTimeMillis() - t0;

            String toolTraceJson = toJson(result.toolTrace());
            String draftJson = result.draft() == null ? null : toJson(result.draft());
            conversationService.appendMessage(conv.getId(), "assistant", result.reply(), toolTraceJson, draftJson, elapsedMs);

            emitter.send(SseEmitter.event().name("done")
                .data(new ChatResponse(result.reply(), result.toolTrace(), result.draft(), conv.getId(), elapsedMs)));
            emitter.complete();
        } catch (Exception e) {
            log.warn("AI 流式对话失败: {}", e.getMessage());
            sendQuiet(emitter, "error", e.getMessage() == null ? "出错了" : e.getMessage());
            emitter.complete();
        }
    }

    private void sendQuiet(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            // 客户端可能已断开，忽略
        }
    }

    /** 我的会话列表 */
    @GetMapping("/conversations")
    public R<List<AiConversation>> conversations() {
        return R.ok(conversationService.listMine());
    }

    /** 某会话的消息（校验归属） */
    @GetMapping("/conversations/{id}/messages")
    public R<List<AiMessage>> messages(@PathVariable Long id) {
        return R.ok(conversationService.listMessages(id));
    }

    /** 删除我的会话 */
    @DeleteMapping("/conversations/{id}")
    public R<Void> deleteConversation(@PathVariable Long id) {
        conversationService.deleteMine(id);
        return R.ok();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("AI 消息序列化失败: {}", e.getMessage());
            return null;
        }
    }

    @Data
    public static class ChatRequest {
        @NotBlank(message = "message 不能为空")
        private String message;
        /** 续聊的会话ID；为空则新建会话 */
        private Long conversationId;
    }

    /** 对话返回：回复 + 工具轨迹 + 草稿 + 会话ID + 耗时 */
    public record ChatResponse(String reply, Object toolTrace, Object draft, Long conversationId, Long elapsedMs) {
    }
}
