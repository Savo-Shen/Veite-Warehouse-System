package com.ruoyi.wms.ai;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * OpenAI tools 数组，按当前登录用户的权限过滤。
     * <p>
     * 只把用户自己有权限调的工具交给模型，模型看不到的工具自然也不会去调。
     */
    public List<Map<String, Object>> specs() {
        return tools.values().stream()
            .filter(ToolRegistry::permitted)
            .map(AiTool::toSpec)
            .toList();
    }

    /**
     * 当前登录用户是否有权使用该工具。
     */
    private static boolean permitted(AiTool tool) {
        String permission = tool.requiredPermission();
        return permission == null || StpUtil.hasPermission(permission);
    }

    /** 该工具是否产出草稿（其结果需额外带回前端供用户确认） */
    public boolean producesDraft(String name) {
        AiTool tool = tools.get(name);
        return tool != null && tool.producesDraft();
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
        if (!permitted(tool)) {
            log.warn("用户无权使用 AI 工具[{}], 需要权限: {}", name, tool.requiredPermission());
            return "{\"error\":\"当前账号没有使用该功能的权限\"}";
        }
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            String result = tool.execute(args);
            return result == null ? "{}" : result;
        } catch (Exception e) {
            log.warn("工具[{}]执行失败, args={}", name, argumentsJson, e);
            return "{\"error\":\"工具执行失败: " + e.getMessage() + "\"}";
        }
    }
}
