package com.ruoyi.wms.ai;

import java.util.List;
import java.util.Map;

/**
 * 大模型客户端抽象层（供应商无关）。
 * <p>
 * 只面向 OpenAI 兼容的 chat/completions 协议，因此一套实现即可对接 DeepSeek、通义、
 * 智谱、Kimi、OpenAI 等，或经由 new-api 等网关聚合。换供应商=换配置，业务代码不变。
 *
 * @author Savo
 */
public interface LlmClient {

    /**
     * 发起一次对话补全。
     *
     * @param messages OpenAI 格式的消息列表（system/user/assistant/tool），用 Map 承载以保持协议透明
     * @param tools    OpenAI 格式的工具定义列表（可为空），形如 {@code {type:"function", function:{...}}}
     * @return 模型返回结果（文本、工具调用、原始 assistant 消息、结束原因）
     */
    LlmResult chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

    /**
     * 流式对话补全：文本增量通过 {@code onContentDelta} 实时回调；返回装配好的完整结果。
     * 默认实现退化为非流式（一次性把整段文本回调出去），供未实现流式的客户端兜底。
     */
    default LlmResult chatStream(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                 java.util.function.Consumer<String> onContentDelta) {
        LlmResult r = chat(messages, tools);
        if (onContentDelta != null && r.content != null && !r.content.isEmpty()) {
            onContentDelta.accept(r.content);
        }
        return r;
    }

    /**
     * 单次工具调用请求。
     */
    record ToolCall(String id, String name, String argumentsJson) {
    }

    /**
     * 模型返回结果。
     */
    class LlmResult {
        /** 助手回复文本（有工具调用时可能为空） */
        public String content;
        /** 模型请求的工具调用（可能为空） */
        public List<ToolCall> toolCalls = List.of();
        /** 原始 assistant 消息（含 tool_calls），需原样塞回下一轮 messages */
        public Map<String, Object> rawAssistantMessage;
        /** 结束原因：stop / tool_calls / length 等 */
        public String finishReason;

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
