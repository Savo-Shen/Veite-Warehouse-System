package com.ruoyi.wms.ai;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.ai.tool.AiTool;
import com.ruoyi.wms.ai.tool.AiToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具注册表：收集所有 {@link AiTool} Bean，提供给模型的工具清单与按名执行。
 *
 * @author Savo
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AiTool> tools = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<AiTool> toolList, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        for (AiTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
        log.info("AI 工具已注册: {}", tools.keySet());
    }

    /**
     * 在<b>请求线程</b>里把全部工具会用到的权限逐个判定一遍，得到当前用户的权限快照。
     * 流式对话跑在工作线程，Sa-Token 在那儿查不到登录态，所以必须在这里先算好带过去。
     */
    public Set<String> grantedPermissions() {
        Set<String> all = new LinkedHashSet<>();
        for (AiTool tool : tools.values()) {
            if (tool.requiredPermission() != null) {
                all.add(tool.requiredPermission());
            }
            all.addAll(tool.permissionsUsed());
        }
        Set<String> granted = new HashSet<>();
        for (String p : all) {
            try {
                if (StpUtil.hasPermission(p)) {
                    granted.add(p);
                }
            } catch (Exception e) {
                log.debug("权限判定失败 {}: {}", p, e.getMessage());
            }
        }
        return granted;
    }

    /**
     * OpenAI tools 数组，按当前用户的权限过滤。
     * <p>
     * 只把用户自己有权限调的工具交给模型，模型看不到的工具自然也不会去调。
     */
    public List<Map<String, Object>> specs() {
        return tools.values().stream()
            .filter(ToolRegistry::visible)
            .map(AiTool::toSpec)
            .toList();
    }

    /**
     * 当前用户能否看到/使用该工具：声明了 requiredPermission 就看它；
     * 只声明了 permissionsUsed（跨出库/入库的工具）则任一有权即可见，细分支由工具自己校验。
     */
    private static boolean visible(AiTool tool) {
        if (tool.requiredPermission() != null) {
            return AiToolContext.has(tool.requiredPermission());
        }
        List<String> used = tool.permissionsUsed();
        return used.isEmpty() || used.stream().anyMatch(AiToolContext::has);
    }

    /** 该工具是否产出草稿（其结果需额外带回前端供用户确认） */
    public boolean producesDraft(String name) {
        AiTool tool = tools.get(name);
        return tool != null && tool.producesDraft();
    }

    /** 工具的中文状态提示 */
    public String title(String name) {
        AiTool tool = tools.get(name);
        return tool == null ? ("正在调用 " + name) : tool.title();
    }

    /** 给模型看的精简结果 */
    public String summarizeForModel(String name, String result) {
        AiTool tool = tools.get(name);
        return tool == null ? result : tool.summarizeForModel(result);
    }

    /**
     * 按名执行工具。出错时返回错误 JSON 文本（而非抛异常），让模型能据此调整。
     */
    public String execute(String name, String argumentsJson) {
        AiTool tool = tools.get(name);
        if (tool == null) {
            return "{\"error\":\"未知工具: " + name + "\"}";
        }
        // 即便 specs() 已经过滤过，这里仍要再查一次：
        // 模型返回的工具名来自模型输出，不能当作可信输入。
        if (!visible(tool)) {
            log.warn("用户无权使用 AI 工具[{}], 需要权限: {}", name, tool.permissionsUsed());
            return "{\"error\":\"当前账号没有使用该功能的权限\"}";
        }
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String result = tool.execute(args);
            return result == null ? "{}" : result;
        } catch (Exception e) {
            log.warn("工具[{}]执行失败, args={}", name, argumentsJson, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return "{\"error\":\"工具执行失败: " + msg.replace("\"", "'") + "\"}";
        }
    }
}
