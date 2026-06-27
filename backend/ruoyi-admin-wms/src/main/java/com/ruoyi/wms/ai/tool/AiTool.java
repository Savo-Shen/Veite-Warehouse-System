package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 可调用的工具（function calling）。
 * <p>
 * 每个工具 = 名称 + 描述 + 入参 JSON Schema + 执行逻辑。执行逻辑直接调用现有 WMS Service，
 * 因此天然继承登录态与权限校验。当前阶段都是只读工具，可自动执行。
 *
 * @author Savo
 */
public interface AiTool {

    /** 工具名（英文、唯一），模型据此调用 */
    String name();

    /** 工具描述：写清楚“什么时候用、能得到什么”，模型据此决定是否调用 */
    String description();

    /** 入参的 JSON Schema（OpenAI function parameters 格式） */
    Map<String, Object> parametersSchema();

    /**
     * 执行工具。
     *
     * @param args 模型给出的入参（已解析为 JsonNode）
     * @return 返回给模型的结果文本，通常为 JSON 字符串
     */
    String execute(JsonNode args) throws Exception;

    /**
     * 是否“产出草稿”的工具。
     * <p>
     * 这类工具不直接落库，其返回的 JSON 会被 orchestrator 额外作为 draft 带回前端，
     * 由用户在现有表单里确认后再走正常保存流程（人在回路）。
     */
    default boolean producesDraft() {
        return false;
    }

    /**
     * 组装成 OpenAI tools 数组里的一项：{@code {type:"function", function:{name,description,parameters}}}
     */
    default Map<String, Object> toSpec() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name());
        function.put("description", description());
        function.put("parameters", parametersSchema());
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", "function");
        spec.put("function", function);
        return spec;
    }
}
