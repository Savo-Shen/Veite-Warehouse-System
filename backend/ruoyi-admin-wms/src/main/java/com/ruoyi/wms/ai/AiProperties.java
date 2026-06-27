package com.ruoyi.wms.ai;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手配置（供应商无关）。
 * <p>
 * 通过 OpenAI 兼容协议接入任意大模型网关（如 new-api）。切换模型/供应商只需改这三项配置。
 * 对应 application.yml 中的 {@code wms.ai.*}。
 *
 * @author Savo
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "wms.ai")
public class AiProperties {

    /** 网关地址，例如 https://llm.savo-shen.com （不含 /v1/chat/completions） */
    private String baseUrl;

    /** 访问密钥（Bearer Token）。建议放在 backend/.env 的 WMS_AI_API_KEY，勿硬编码提交 */
    private String apiKey;

    /** 模型名，例如 deepseek-v4-flash */
    private String model;

    /** 单次会话内允许的最大“工具调用 → 再问模型”轮次，防止死循环 */
    private int maxToolRounds = 5;

    /** 请求超时（秒） */
    private int timeoutSeconds = 60;

    /** 失败重试次数（针对偶发的网络中断、限流、上游渠道欠费等可换渠道重试的情况） */
    private int maxRetries = 3;

    /** 可选 HTTP 代理主机（如本机 clash/v2ray：127.0.0.1）。留空则直连 */
    private String proxyHost;

    /** 可选 HTTP 代理端口（如 7890）。需与 proxyHost 同时配置 */
    private Integer proxyPort;

    /** 启动时打印一次配置状态（密钥脱敏），便于排查“是否真的读到了 .env”。 */
    @PostConstruct
    public void logStatus() {
        boolean keyPresent = apiKey != null && !apiKey.isBlank();
        String proxy = (proxyHost != null && !proxyHost.isBlank() && proxyPort != null)
            ? (proxyHost + ":" + proxyPort) : "直连(无代理)";
        log.info("[WMS-AI] 配置加载: baseUrl={}, model={}, apiKey={}, 代理={}",
            baseUrl, model,
            keyPresent ? ("已配置(长度" + apiKey.length() + ")") : "未配置(空)",
            proxy);
    }
}
