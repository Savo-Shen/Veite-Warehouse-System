package com.ruoyi.wms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议的大模型客户端实现。
 * <p>
 * 用 JDK 原生 HttpClient + Jackson，无额外依赖。请求 {@code {baseUrl}/v1/chat/completions}。
 *
 * @author Savo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatLlmClient implements LlmClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    private HttpClient client() {
        if (httpClient == null) {
            HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
            if (props.getProxyHost() != null && !props.getProxyHost().isBlank() && props.getProxyPort() != null) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(props.getProxyHost(), props.getProxyPort())));
            }
            httpClient = builder.build();
        }
        return httpClient;
    }

    /** 配置校验 + 构造请求（stream 决定是否走流式） */
    private HttpRequest buildRequest(List<Map<String, Object>> messages, List<Map<String, Object>> tools, boolean stream, String model) {
        boolean baseUrlMissing = props.getBaseUrl() == null || props.getBaseUrl().isBlank();
        boolean keyMissing = props.getApiKey() == null || props.getApiKey().isBlank();
        if (baseUrlMissing) {
            throw new ServiceException("AI 配置未绑定：未读到 wms.ai.base-url，请确认 application.yml 的 wms.ai 配置块生效并已重启后端");
        }
        if (keyMissing) {
            throw new ServiceException("AI 密钥未注入：base-url 已读到(" + props.getBaseUrl()
                + ")，但 WMS_AI_API_KEY 为空。请在 backend 目录用 dev-start.sh / dev-start.ps1 启动后端，或把密钥设进当前启动环境变量");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model == null || model.isBlank() ? props.getModel() : model);
        body.put("messages", messages);
        body.put("stream", stream);
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        String url = props.getBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions";
        String reqJson;
        try {
            reqJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ServiceException("AI 请求体序列化失败：" + e.getMessage());
        }

        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + props.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
            .build();
    }

    @Override
    public LlmResult chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools, String model) {
        HttpRequest request = buildRequest(messages, tools, false, model);

        int lastStatus = -1;
        String lastBody = null;
        Exception lastError = null;
        int attempts = Math.max(1, props.getMaxRetries() + 1);

        for (int i = 0; i < attempts; i++) {
            try {
                HttpResponse<String> resp = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int sc = resp.statusCode();
                if (sc >= 200 && sc < 300) {
                    return parse(resp.body());
                }
                lastStatus = sc;
                lastBody = resp.body();
                boolean retryable = isRetryable(sc, lastBody);
                log.warn("LLM 网关返回 {}（第{}/{}次{}）body={}", sc, i + 1, attempts, retryable ? ",可重试" : "", lastBody);
                if (!retryable) {
                    throw new ServiceException(buildHttpError(sc, lastBody));
                }
            } catch (ServiceException se) {
                throw se; // 不可重试的明确错误，直接抛
            } catch (Exception e) {
                lastError = e;
                log.warn("调用大模型网关第{}/{}次异常: {}", i + 1, attempts, e.getMessage());
            }
            if (i < attempts - 1) {
                sleepBackoff(i);
            }
        }

        if (lastBody != null) {
            throw new ServiceException(buildHttpError(lastStatus, lastBody) + "（重试" + attempts + "次仍失败）");
        }
        String msg = lastError == null ? "未知错误" : lastError.getMessage();
        throw new ServiceException("调用大模型网关失败（重试" + attempts + "次仍失败）：" + msg
            + "。提示：网关 " + props.getBaseUrl() + " 若经 Cloudflare 可能被网络层重置握手，可配置 wms.ai.proxy-host/proxy-port 走本机代理");
    }

    private void sleepBackoff(int i) {
        try {
            Thread.sleep(300L * (i + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 这些情况换个渠道重试通常能成：限流/超时/5xx，以及 new-api 渠道欠费或无可用渠道 */
    private boolean isRetryable(int status, String body) {
        if (status == 429 || status == 408 || status == 409 || status >= 500) {
            return true;
        }
        return body != null && (body.contains("Arrearage") || body.contains("欠费")
            || body.contains("no_available_channel") || body.contains("无可用渠道"));
    }

    private String buildHttpError(int status, String body) {
        if (body != null && (body.contains("Arrearage") || body.contains("欠费"))) {
            return "上游渠道欠费(Arrearage)：请在 new-api 后台为模型 " + props.getModel()
                + " 充值或停用欠费渠道。网关返回：" + truncate(body);
        }
        return "大模型网关返回 " + status + "：" + truncate(body);
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    /** 临时累积流式返回的单个工具调用 */
    private static class ToolAcc {
        String id;
        String name;
        final StringBuilder args = new StringBuilder();
    }

    @Override
    public LlmResult chatStream(List<Map<String, Object>> messages, List<Map<String, Object>> tools, String model,
                                Consumer<String> onContentDelta) {
        HttpRequest request = buildRequest(messages, tools, true, model);
        int attempts = Math.max(1, props.getMaxRetries() + 1);
        Exception lastError = null;

        for (int i = 0; i < attempts; i++) {
            // 已经往前端吐过文字就不能再重试了，否则用户会看到重复的一段
            boolean[] emitted = {false};
            try {
                HttpResponse<Stream<String>> resp = client().send(request, HttpResponse.BodyHandlers.ofLines());
                int sc = resp.statusCode();
                if (sc < 200 || sc >= 300) {
                    String body;
                    try (Stream<String> lines = resp.body()) {
                        body = lines.collect(Collectors.joining("\n"));
                    }
                    boolean retryable = isRetryable(sc, body) && i < attempts - 1;
                    log.warn("LLM 流式返回 {}（第{}/{}次{}） body={}", sc, i + 1, attempts, retryable ? ",可重试" : "", truncate(body));
                    if (!retryable) {
                        throw new ServiceException(buildHttpError(sc, body));
                    }
                    sleepBackoff(i);
                    continue;
                }
                return consumeStream(resp, onContentDelta, emitted);
            } catch (ServiceException se) {
                throw se;
            } catch (Exception e) {
                lastError = e;
                log.warn("调用大模型网关(流式)第{}/{}次异常: {}", i + 1, attempts, e.getMessage());
                if (emitted[0] || i == attempts - 1) {
                    break;
                }
                sleepBackoff(i);
            }
        }
        String msg = lastError == null ? "未知错误" : lastError.getMessage();
        throw new ServiceException("调用大模型网关失败（重试" + attempts + "次仍失败）：" + msg);
    }

    /** 逐行消费 SSE 流，把文本增量实时回调、工具调用片段按 index 拼装 */
    private LlmResult consumeStream(HttpResponse<Stream<String>> resp, Consumer<String> onContentDelta, boolean[] emitted) {
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolAcc> toolAcc = new TreeMap<>();
        String[] finishReason = {null};
        JsonNode[] usage = {null};

        try (Stream<String> lines = resp.body()) {
            lines.forEach(line -> {
                if (line == null || line.isEmpty() || !line.startsWith("data:")) {
                    return;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    return;
                }
                try {
                    JsonNode root = objectMapper.readTree(data);
                    if (root.hasNonNull("usage")) {
                        usage[0] = root.get("usage");
                    }
                    JsonNode choice = root.path("choices").path(0);
                    JsonNode fr = choice.path("finish_reason");
                    if (fr.isTextual()) {
                        finishReason[0] = fr.asText();
                    }
                    JsonNode delta = choice.path("delta");
                    JsonNode c = delta.path("content");
                    if (c.isTextual() && !c.asText().isEmpty()) {
                        content.append(c.asText());
                        if (onContentDelta != null) {
                            emitted[0] = true;
                            onContentDelta.accept(c.asText());
                        }
                    }
                    JsonNode tcs = delta.path("tool_calls");
                    if (tcs.isArray()) {
                        for (JsonNode tc : tcs) {
                            ToolAcc acc = toolAcc.computeIfAbsent(tc.path("index").asInt(0), k -> new ToolAcc());
                            if (tc.hasNonNull("id")) {
                                acc.id = tc.get("id").asText();
                            }
                            JsonNode fn = tc.path("function");
                            if (fn.hasNonNull("name")) {
                                acc.name = fn.get("name").asText();
                            }
                            if (fn.hasNonNull("arguments")) {
                                acc.args.append(fn.get("arguments").asText());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("跳过无法解析的流片段: {}", data);
                }
            });
        }
        if (usage[0] != null) {
            log.info("LLM 用量: prompt={} completion={}", usage[0].path("prompt_tokens").asInt(), usage[0].path("completion_tokens").asInt());
        }

        LlmResult result = new LlmResult();
        result.content = content.toString();
        result.finishReason = finishReason[0];

        List<ToolCall> calls = new ArrayList<>();
        List<Map<String, Object>> toolCallsJson = new ArrayList<>();
        for (ToolAcc acc : toolAcc.values()) {
            String argsStr = acc.args.length() == 0 ? "{}" : acc.args.toString();
            calls.add(new ToolCall(acc.id, acc.name, argsStr));
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", acc.name);
            fn.put("arguments", argsStr);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", acc.id);
            one.put("type", "function");
            one.put("function", fn);
            toolCallsJson.add(one);
        }
        result.toolCalls = calls;

        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", content.length() == 0 ? null : content.toString());
        if (!toolCallsJson.isEmpty()) {
            assistant.put("tool_calls", toolCallsJson);
        }
        result.rawAssistantMessage = assistant;
        return result;
    }

    private LlmResult parse(String responseBody) {
        LlmResult result = new LlmResult();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");

            result.finishReason = choice.path("finish_reason").asText(null);
            if (message.hasNonNull("content")) {
                result.content = message.get("content").asText();
            }

            // 原样保留 assistant 消息（含 tool_calls），下一轮要塞回去
            result.rawAssistantMessage = objectMapper.convertValue(message, Map.class);

            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode tc = message.path("tool_calls");
            if (tc.isArray()) {
                for (JsonNode call : tc) {
                    String id = call.path("id").asText();
                    JsonNode fn = call.path("function");
                    String name = fn.path("name").asText();
                    String args = fn.path("arguments").asText("{}");
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }
            result.toolCalls = toolCalls;
            return result;
        } catch (Exception e) {
            log.warn("解析 LLM 响应失败, body={}", responseBody, e);
            throw new ServiceException("解析大模型响应失败：" + e.getMessage());
        }
    }
}
