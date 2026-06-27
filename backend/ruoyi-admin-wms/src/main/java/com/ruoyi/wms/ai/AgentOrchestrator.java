package com.ruoyi.wms.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 调度器：经典的 tool-use 循环（大脑）。
 * <p>
 * 调模型 → 若模型要调工具则执行工具、把结果塞回去再问 → 直到模型给出最终回复或达到最大轮次。
 * 与具体供应商无关，只面向 {@link LlmClient} 与 {@link ToolRegistry}。
 *
 * @author Savo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final AiProperties props;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是仓库管理系统(WMS)的智能助手。你可以调用工具查询商品价格、库存等信息。
        规则：
        1. 需要数据时必须调用工具获取，不要凭空编造商品、价格或库存。
        2. 用简洁的中文回答，必要时用表格或列表罗列结果。
        3. 搜索关键字尽量拆成独立的词、用空格分隔，比如“气管 10*6.5”而不是“10*6.5气管”；
           商品名和规格/尺寸分开给，尺寸本身(如 10*6.5)保持完整不要拆。
        4. 如果工具返回 0 条结果，不要立刻让用户换词——先自己用更宽松或拆分后的关键字
           再调用 1~2 次（例如只用商品名“气管”、或只用尺寸“10*6.5”），仍然查不到再告知未找到。
        5. 当用户想“给某客户出库一批商品/开出库单/发货/卖出/卖给/销售给/发给/寄给”，
           或者说“帮我记一下/记一笔/登记一下/记录一下”某笔卖给客户的商品时，
           都视为创建出库单草稿，调用 create_shipment_draft 生成草稿，不要回答“没有记账功能”。
           （这不会真正出库，只是生成草稿）。生成后简要说明草稿内容，并提醒用户“已生成出库草稿，
           请在打开的表单中核对并确认”。如果草稿里有 warnings 或 unresolved（没匹配到的商品/客户），
           要明确告诉用户哪些需要手动处理。
        6. 当用户想“从某供应商入库/采购入库/生产入库/补库存/开入库单/进货/到货/收货”时，
           都视为入库，调用 create_receipt_draft
           生成入库草稿（这不会真正入库，只是生成草稿）。生成后简要说明草稿内容，并提醒用户
           “已生成入库草稿，请在打开的表单中核对并确认”。如果草稿里有 warnings 或 unresolved，
           要明确告诉用户哪些需要手动处理。
        7. 商品可能有俗称或别名，例如“PU管”也可能被用户叫“气管”。遇到俗称时仍按用户原话调用工具，
           不要因为名称不完全一致就判定没有商品。
        8. 数量带单位时要转成数字并保留原单位信息：例如“一卷气管”传 quantity=1，remark 写明“单位：卷”；
           “两箱/2箱/3包”分别按 2/2/3 处理。商品名称里保留规格，如“10*6.5气管”。
        9. 建单时直接调用对应的草稿工具（create_shipment_draft / create_receipt_draft），
           不要先用 search_sku 或 query_inventory 去“确认商品是否存在”——草稿工具内部会自己匹配商品，
           多余的预查询只会增加等待时间。一次说清就一步到位。
        """;

    /**
     * 处理一次用户输入，返回最终回复与工具调用轨迹。
     */
    public AgentResult chat(String userMessage) {
        return chat(userMessage, List.of());
    }

    /**
     * 带历史上下文的对话（同一会话内多轮记忆）。
     *
     * @param userMessage 本轮用户输入
     * @param history     之前的消息（每项 {role, content}，按时间正序）
     */
    public AgentResult chat(String userMessage, List<Map<String, Object>> history) {
        return run(userMessage, history, null);
    }

    /** 流式对话：文本增量与工具状态通过 sink 实时回调。 */
    public AgentResult chatStream(String userMessage, List<Map<String, Object>> history, StreamSink sink) {
        return run(userMessage, history, sink);
    }

    private AgentResult run(String userMessage, List<Map<String, Object>> history, StreamSink sink) {
        List<Map<String, Object>> messages = buildMessages(userMessage, history);
        List<Map<String, Object>> tools = toolRegistry.specs();
        List<ToolTrace> trace = new ArrayList<>();
        Object draft = null;

        for (int round = 0; round < props.getMaxToolRounds(); round++) {
            LlmClient.LlmResult result = (sink == null)
                ? llmClient.chat(messages, tools)
                : llmClient.chatStream(messages, tools, sink::onDelta);

            if (!result.hasToolCalls()) {
                return new AgentResult(result.content, trace, draft);
            }

            // 把模型的 assistant 消息(含 tool_calls)原样塞回去
            messages.add(result.rawAssistantMessage);

            // 逐个执行工具，结果作为 role=tool 消息追加
            for (LlmClient.ToolCall call : result.toolCalls) {
                if (sink != null) {
                    sink.onStatus("正在调用工具：" + call.name());
                }
                String toolResult = toolRegistry.execute(call.name(), call.argumentsJson());
                trace.add(new ToolTrace(call.name(), call.argumentsJson(), toolResult));
                messages.add(toolMessage(call.id(), toolResult));
                // 草稿类工具：把结果额外解析为结构化草稿带回前端（人在回路确认）
                if (toolRegistry.producesDraft(call.name())) {
                    draft = parseDraft(toolResult);
                }
            }
        }

        log.warn("Agent 达到最大工具轮次({})仍未结束", props.getMaxToolRounds());
        return new AgentResult("（处理较复杂，已达到最大工具调用轮次，请把问题拆细一点再试）", trace, draft);
    }

    private List<Map<String, Object>> buildMessages(String userMessage, List<Map<String, Object>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", SYSTEM_PROMPT));
        if (history != null) {
            for (Map<String, Object> h : history) {
                Object role = h.get("role");
                Object content = h.get("content");
                if (role != null && content != null) {
                    messages.add(message(role.toString(), content.toString()));
                }
            }
        }
        messages.add(message("user", userMessage));
        return messages;
    }

    /** 流式回调：工具调用状态 + 文本增量 */
    public interface StreamSink {
        void onStatus(String status);

        void onDelta(String delta);
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private Map<String, Object> toolMessage(String toolCallId, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", toolCallId);
        m.put("content", content);
        return m;
    }

    private Object parseDraft(String toolResult) {
        try {
            return objectMapper.readValue(toolResult, Map.class);
        } catch (Exception e) {
            log.warn("解析草稿失败: {}", e.getMessage());
            return null;
        }
    }

    /** Agent 最终结果。draft 不为空时，前端据此预填表单供用户确认 */
    public record AgentResult(String reply, List<ToolTrace> toolTrace, Object draft) {
    }

    /** 一次工具调用的轨迹（便于前端调试/展示“AI 查了什么”） */
    public record ToolTrace(String tool, String arguments, String result) {
    }
}
