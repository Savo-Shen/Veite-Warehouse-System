package com.ruoyi.wms.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.ai.tool.AiToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final com.ruoyi.wms.service.WarehouseService warehouseService;

    private static final String SYSTEM_PROMPT = """
        你是仓库管理系统(WMS)的智能助手，代表当前登录的仓库员工操作系统。今天是 %s。
        系统里的仓库：%s。

        ## 你能做什么（都通过工具完成）
        - 查商品/价格：search_sku。查库存：query_inventory（可按仓库、库存上下限、只看负库存筛选，不给关键字也能用）。
        - 查往来单位：search_merchant（客户/供应商/物流单位的联系人、电话、地址）。
        - 查单据列表：query_orders（按单号片段、客户/供应商、商品、日期范围、状态找出库单/入库单）。
        - 看整张单：get_order（按单号调出每一行的商品、数量、单价、金额、当前售价/进价，以及还能不能改）。
        - 查出入库明细历史：query_order_history（某商品卖给过谁/什么价、某客户买过什么、某段时间的记录，结果带 skuId）。
        - 生成出库单草稿：create_shipment_draft；生成入库单草稿：create_receipt_draft。
          两者都支持 copyFromOrderNo（复制一张已有单据，明细/客户/仓库自动带入）、priceMode（售价/进价/该客户上次成交价/来源单价/0）、
          每行明确的 price，以及 items 里直接给 skuId。
        - 修改还能改的单据：modify_order_draft（改数量、改单价、整单换单价模式、增删行、改备注）。
          只有“未出库/未入库”或“纯记录”的单据能改；已出库/已入库/作废的单据明细已锁定，只能复制成新单。
        - 盘点草稿：create_check_draft（用户报实盘数，工具带出账面数算差异）；移库草稿：create_movement_draft（仓库之间调拨）；
          换货架库位：relocate_sku_draft（“把 X 放到 2-B2-2”这种是库位调整，不是移库单）。
        - 库存流水：query_inventory_history（含盘点/移库，有变动前后数量，用于“库存怎么变成这个数的”）。
        - 统计：query_stats（按客户/商品/规格/分类/月/日汇总数量、金额、成本、毛利，数据库直接算）；经营概览：get_dashboard（今天怎么样、库存值多少钱、最近卖得最好的）。
          问“多少钱/多少件/哪个最多/每个月”这类汇总问题一律用 query_stats，不要拉明细自己加。
        - 压油管：hose_quote（通径+层数+长度+两端接头 → 配料、库位、成本、售价、能不能现场压）；hose_stock（胶管各段/接头/外套的库存）。
          通径、接头都按用户原话传给工具（四分、22×1.5 A型芯…），工具会换算。回复客户口径只说售价，进价只给员工看。
        - 报价单：make_quotation（按售价/上次成交价/进价加成算价，带毛利，给出可直接发客户的文字；不建单）。
        - 建档：create_item_draft（新建商品和规格；商品已存在则补规格）、create_merchant_draft（新建客户/供应商/物流单位）。
        所有“草稿”都不会直接保存：出入库/盘点/移库草稿会在前端表单里预填，由用户核对后自己点保存；
        建档和库位调整是“待确认操作”，用户要在对话里点“确认执行”才生效——你只能说“已整理好，请点确认执行”，不能说“已建好/已改好”。

        ## 工作方式
        1. 复杂任务拆成几步，按顺序调用工具完成，不要因为某一步“没有直接的功能”就整体回答做不到。例如：
           - “把 CK08054357 复制一份，价格改成进价” → create_shipment_draft(copyFromOrderNo="CK08054357", priceMode="cost")，一步到位。
           - “把上个月卖给约克的东西再出一单” → query_order_history(merchant="约克", beginDate, endDate) → create_shipment_draft(customer="约克", items 用返回的 skuId)。
           - “看看 RK08186356 里有什么，然后照它再进一批” → get_order → create_receipt_draft(copyFromOrderNo=...)。
           - “把 CK10238365 里气管的数量改成 5” → modify_order_draft(orderNo, items=[{name:"气管", quantity:5}])；工具若说单据已锁定，就改用复制并告诉用户原因。
        2. 需要数据必须调工具，绝不编造商品、价格、库存、单号、客户。一次可以并行调多个工具（比如同时查几个商品的库存）。
        3. 工具返回 0 条时，先自己换更宽松或拆分后的关键字再试 1~2 次（只用商品名、或只用规格/尺寸），仍然查不到再告知未找到。
        4. 拿到 skuId 之后建单/改单时直接传 skuId，避免二次模糊匹配错商品；草稿返回 unresolved 时，可以先用 search_sku 换词再查，把找到的 skuId 补进 items 重新生成，而不是直接把问题丢给用户。
        5. 搜索关键字：商品名和规格分开、用空格隔开（“气管 10*6.5”而不是“10*6.5气管”），规格本身（10*6.5）保持完整不要拆；
           俗称按用户原话搜（PU管≈气管），别因为名称不完全一致就判定没有这个商品。
        6. 数量带单位时 quantity 填数字、unit 填单位（“一卷气管”→ quantity=1, unit=卷）。同一商品常有“按米”和“按卷/捆”两条记录，
           系统按 unit 自动挑，务必带上 unit，绝不能把“一卷”当成 1 米。
        7. 用户说“卖给/发给/寄给/出库/记一下/记一笔/登记一下”某客户某商品，都视为出库草稿；“进货/到货/收货/采购/补库存/入库”视为入库草稿。
           “改成进价/按成本价”= priceMode=cost；“按上次价/老价格”= priceMode=last；“按售价/零售价”= priceMode=selling。
        8. 日期：“上个月/本周/最近三天/昨天”自行换算成 yyyy-MM-dd（以今天为准）。
        9. 必要信息确实缺失（比如要建出库单却完全不知道商品是什么）时，用一句话问清楚；可选信息（仓库、备注、客户）留空即可，不要反复追问。
           用户已经把要求说完整时不要再确认，直接做。
        10. 回答用简洁的中文；多条数据用 Markdown 表格；金额保留两位小数。生成草稿后简要列出草稿内容（对方、几行、合计），
            提醒“已生成草稿，请在打开的表单中核对确认”，并把 warnings 和 unresolved 里需要人工处理的项明确列出来。
        11. 最终回复面向仓库员工说人话，不要罗列工具名、参数名或 skuId 之类的内部字段。
        12. 建档前先用 search_sku / search_merchant 确认没有重复；盘点时用户说“实际有 N 个”“数出来是 N”就是实盘数。
        13. “确认执行”按钮只有在你真的调用了 create_item_draft / create_merchant_draft / relocate_sku_draft 之后才会出现在对话里；
            表单预填按钮也只有调用了对应的 *_draft 工具才会出现。确认没有重复之后必须紧接着调用建档/调整工具，
            不调用工具就告诉用户“已整理好，请点确认执行”是错误的，用户会看不到任何按钮。
        """;

    /**
     * 处理一次用户输入，返回最终回复与工具调用轨迹。
     *
     * @param granted 当前用户的权限快照（在请求线程里由 {@link ToolRegistry#grantedPermissions()} 算好）
     */
    public AgentResult chat(String userMessage, List<Map<String, Object>> history, Set<String> granted, String mode) {
        return run(userMessage, history, granted, mode, null);
    }

    /** 流式对话：文本增量与工具状态通过 sink 实时回调。 */
    public AgentResult chatStream(String userMessage, List<Map<String, Object>> history, Set<String> granted,
                                  String mode, StreamSink sink) {
        return run(userMessage, history, granted, mode, sink);
    }

    private AgentResult run(String userMessage, List<Map<String, Object>> history, Set<String> granted,
                            String mode, StreamSink sink) {
        AiToolContext.set(granted);
        try {
            return doRun(userMessage, history, props.resolveModel(mode), sink);
        } finally {
            AiToolContext.clear();
        }
    }

    private AgentResult doRun(String userMessage, List<Map<String, Object>> history, String model, StreamSink sink) {
        List<Map<String, Object>> messages = buildMessages(userMessage, history);
        List<Map<String, Object>> tools = toolRegistry.specs();
        List<ToolTrace> trace = new ArrayList<>();
        Object draft = null;
        boolean nudged = false;

        for (int round = 0; round < props.getMaxToolRounds(); round++) {
            LlmClient.LlmResult result = (sink == null)
                ? llmClient.chat(messages, tools, model)
                : llmClient.chatStream(messages, tools, model, sink::onDelta);

            if (!result.hasToolCalls()) {
                String text = finalText(result);
                // 模型偶尔会“口头”说已生成草稿/请点确认执行，却没真的调工具——用户就看不到任何按钮。
                // 抓到这种情况提醒它一次，让它把工具补上。
                if (draft == null && !nudged && claimsDraftWithoutTool(text)) {
                    nudged = true;
                    log.info("模型声称已生成草稿但未调用工具，提醒补调");
                    if (sink != null) {
                        sink.onStatus("正在补生成草稿");
                    }
                    messages.add(result.rawAssistantMessage);
                    messages.add(message("user", "（系统提示：你刚才没有调用任何草稿/建档/调整工具，用户看不到“确认执行”或表单按钮。"
                        + "请立即调用对应的 *_draft 工具生成草稿，然后再简短答复。）"));
                    continue;
                }
                return new AgentResult(text, trace, draft, model);
            }

            // 把模型的 assistant 消息(含 tool_calls)原样塞回去
            messages.add(result.rawAssistantMessage);

            // 逐个执行工具，结果作为 role=tool 消息追加
            for (LlmClient.ToolCall call : result.toolCalls) {
                if (sink != null) {
                    sink.onStatus(toolRegistry.title(call.name()));
                }
                long t0 = System.currentTimeMillis();
                String toolResult = toolRegistry.execute(call.name(), call.argumentsJson());
                log.info("AI 工具 {} 耗时 {}ms, 结果 {} 字符", call.name(), System.currentTimeMillis() - t0, toolResult.length());
                trace.add(new ToolTrace(call.name(), call.argumentsJson(), toolResult));
                // 模型只需要精简版；完整结果留给前端（草稿预填、轨迹展示）
                messages.add(toolMessage(call.id(), toolRegistry.summarizeForModel(call.name(), toolResult)));
                // 草稿类工具：把结果额外解析为结构化草稿带回前端（人在回路确认）
                if (toolRegistry.producesDraft(call.name())) {
                    Object parsed = parseDraft(toolResult);
                    if (parsed != null) {
                        draft = parsed;
                    }
                }
            }
        }

        // 轮次用完：不带工具再问一次，让模型基于已拿到的信息收个尾，而不是直接甩一句“太复杂”
        log.warn("Agent 达到最大工具轮次({})仍未结束，强制收尾", props.getMaxToolRounds());
        messages.add(message("user", "（系统提示：本轮工具调用次数已达上限，请不要再调用工具，直接根据已经获得的信息给出结论；"
            + "如果还缺信息，说明还需要用户补充什么。）"));
        try {
            LlmClient.LlmResult last = (sink == null)
                ? llmClient.chat(messages, List.of(), model)
                : llmClient.chatStream(messages, List.of(), model, sink::onDelta);
            return new AgentResult(finalText(last), trace, draft, model);
        } catch (Exception e) {
            log.warn("强制收尾失败: {}", e.getMessage());
            return new AgentResult("（这个任务步骤比较多，已达到单次工具调用上限。请把问题拆细一点分两次说，或告诉我先做哪一步）", trace, draft, model);
        }
    }

    /** 回复里说了“草稿/确认执行/表单”这类只有工具才能兑现的话 */
    private static boolean claimsDraftWithoutTool(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("确认执行") || text.contains("草稿已生成") || text.contains("已生成草稿")
            || text.contains("已生成") && text.contains("草稿") || text.contains("打开的表单") || text.contains("已整理好");
    }

    private static String finalText(LlmClient.LlmResult result) {
        String content = result.content == null ? "" : result.content.trim();
        if (content.isEmpty()) {
            content = "（模型没有返回内容，请换个说法再试一次）";
        }
        if ("length".equals(result.finishReason)) {
            content += "\n\n（回复太长被截断了，可以让我分开说）";
        }
        return content;
    }

    private List<Map<String, Object>> buildMessages(String userMessage, List<Map<String, Object>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", SYSTEM_PROMPT.formatted(today(), warehouses())));
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

    /** 仓库就那么一两个，直接写进提示词，省得模型为了找“二号仓库”反复试探 */
    private String warehouses() {
        try {
            List<String> names = warehouseService.queryList(new com.ruoyi.wms.domain.bo.WarehouseBo()).stream()
                .map(com.ruoyi.wms.domain.vo.WarehouseVo::getWarehouseName).toList();
            if (names.isEmpty()) {
                return "（还没有建仓库）";
            }
            String joined = String.join("、", names);
            return names.size() == 1
                ? joined + "（只有这一个仓库，所以不存在仓库之间的移库；用户说“移到/挪到某处”基本都是换货架库位，用 relocate_sku_draft）"
                : joined;
        } catch (Exception e) {
            log.debug("读取仓库列表失败: {}", e.getMessage());
            return "（未知）";
        }
    }

    private static String today() {
        LocalDate d = LocalDate.now();
        return d + " " + d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
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

    /** 只有真正的草稿（带 type）才算；工具返回的错误 JSON 不能覆盖掉之前生成的草稿 */
    private Object parseDraft(String toolResult) {
        try {
            Map<?, ?> m = objectMapper.readValue(toolResult, Map.class);
            return m.get("type") == null ? null : m;
        } catch (Exception e) {
            log.warn("解析草稿失败: {}", e.getMessage());
            return null;
        }
    }

    /** Agent 最终结果。draft 不为空时，前端据此预填表单供用户确认；model 是本次实际用的模型 */
    public record AgentResult(String reply, List<ToolTrace> toolTrace, Object draft, String model) {
    }

    /** 一次工具调用的轨迹（便于前端调试/展示“AI 查了什么”） */
    public record ToolTrace(String tool, String arguments, String result) {
    }
}
