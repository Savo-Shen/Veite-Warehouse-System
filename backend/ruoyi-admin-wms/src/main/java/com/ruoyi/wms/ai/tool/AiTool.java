package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;

import java.util.LinkedHashMap;
import java.util.List;
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
     * 计量单位归一化分组。把口语单位和系统登记单位归到同一组，
     * 例如用户说“卷”、系统登记“捆”都归到“卷”组，从而能匹配上同一类计量的商品。
     */
    Map<String, String> UNIT_GROUP = Map.ofEntries(
        Map.entry("卷", "卷"), Map.entry("捆", "卷"), Map.entry("盘", "卷"),
        Map.entry("圈", "卷"), Map.entry("巻", "卷"), Map.entry("roll", "卷"),
        Map.entry("米", "米"), Map.entry("m", "米"), Map.entry("公尺", "米"),
        Map.entry("箱", "箱"),
        Map.entry("包", "包"), Map.entry("袋", "包"),
        Map.entry("个", "个"), Map.entry("只", "个"), Map.entry("件", "个"),
        Map.entry("支", "个"), Map.entry("根", "个"), Map.entry("pcs", "个"), Map.entry("pc", "个")
    );

    /** 把单位归一化到分组键；未知单位返回其自身（去括号/小写） */
    static String canonicalUnit(String unit) {
        if (unit == null) {
            return null;
        }
        // 去掉括号及其内容，如 "PU管(米)" 里的修饰；只取核心单位词
        String t = unit.trim().toLowerCase().replaceAll("[()（）\\s]", "");
        if (t.isEmpty()) {
            return null;
        }
        return UNIT_GROUP.getOrDefault(t, t);
    }

    /** 两个单位是否属于同一计量分组（卷≈捆、米≈m 等） */
    static boolean sameUnitGroup(String a, String b) {
        String ca = canonicalUnit(a);
        String cb = canonicalUnit(b);
        return ca != null && ca.equals(cb);
    }

    /**
     * 在多个匹配商品中，按用户所说单位挑出对应计量方式的那个（如“一卷”挑单位为“捆/卷”的商品）。
     * 匹配不到则返回第一个。
     */
    static ItemSkuMapVo pickByUnit(List<ItemSkuMapVo> rows, String unit) {
        if (unit != null && !unit.isBlank() && rows.size() > 1) {
            for (ItemSkuMapVo r : rows) {
                String itemUnit = r.getItem() == null ? null : r.getItem().getUnit();
                if (sameUnitGroup(itemUnit, unit)) {
                    return r;
                }
            }
        }
        return rows.get(0);
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
