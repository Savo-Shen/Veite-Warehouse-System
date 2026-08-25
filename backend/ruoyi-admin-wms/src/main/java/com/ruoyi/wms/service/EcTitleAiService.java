package com.ruoyi.wms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.wms.ai.LlmClient;
import com.ruoyi.wms.domain.vo.EcListingSkuVo;
import com.ruoyi.wms.domain.vo.EcProductVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 电商标题的 AI 生成。
 * <p>
 * 复用 {@link LlmClient}（OpenAI 兼容协议，经 new-api 网关），换模型只需改 wms.ai.* 配置。
 * 提示词只喂库里确有的事实：品类名、品牌、电商类目、解析出的规格参数与属性。
 * 不喂重量与包装尺寸——那两项库里本来就是空的，喂了会让模型编。
 *
 * @author savo
 * @date 2026-08-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcTitleAiService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是中国电商平台（拼多多/淘宝/抖音）的工业品类目运营，专做气动元件与液压元件。
        任务：根据给定的商品事实，写一条商品标题。

        硬性要求：
        1. 长度 30~60 个汉字，不要超出。
        2. 只能使用我给你的事实，不得编造材质、压力、温度、认证、产地、销量、保修。
        3. 结构建议：品牌 + 品类全称 + 关键规格 + 适用场景/卖点。
        4. 允许包含「亚德客型」这类兼容性描述——这是买家的真实搜索词。
        5. 不要出现「包邮」「特价」「第一」「最」等平台违禁或极限词。
        6. 不要加书名号、引号、emoji。词与词之间用空格分隔，不要用逗号堆砌。
        7. 若给了多个品牌，选最主流的 1~2 个写进标题，不要全列。

        只返回 JSON，形如：{"title":"...","points":["卖点1","卖点2","卖点3"]}
        points 为 2~4 条短句，每条不超过 20 字，同样只能基于给定事实。
        """;

    /**
     * 为单个商品生成标题与卖点。
     *
     * @param product   商品视图（含类目、品牌、属性）
     * @param skus      该商品的 SKU 明细，用于提取规格覆盖范围
     * @param extraHint 额外风格要求，可为空
     * @return title 与 points
     */
    public GenResult generate(EcProductVo product, List<EcListingSkuVo> skus, String extraHint) {
        String userPrompt = buildUserPrompt(product, skus, extraHint);

        List<Map<String, Object>> messages = List.of(
            Map.of("role", "system", "content", SYSTEM_PROMPT),
            Map.of("role", "user", "content", userPrompt)
        );

        LlmClient.LlmResult r = llmClient.chat(messages, null);
        String content = r.content == null ? "" : r.content.trim();
        if (content.isEmpty()) {
            throw new ServiceException("AI 未返回内容");
        }
        return parse(content, product.getEcName());
    }

    /** 把商品事实组装成提示词。只放库里确有的东西。 */
    private String buildUserPrompt(EcProductVo product, List<EcListingSkuVo> skus, String extraHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("品类名：").append(product.getEcName()).append('\n');
        sb.append("电商类目：").append(nullToDash(product.getEcCategoryPath())).append('\n');
        sb.append("品牌：").append(nullToDash(product.getBrands())).append('\n');
        sb.append("规格数量：").append(product.getSkuCount() == null ? 0 : product.getSkuCount()).append(" 个\n");

        // 商品级属性（材质、使用压力、使用温度、使用介质、规格范围等）
        Map<String, Object> attrs = readJsonMap(product.getAttrs());
        if (!attrs.isEmpty()) {
            sb.append("商品属性：\n");
            attrs.forEach((k, v) -> {
                if (k.startsWith("_") || "可选规格数".equals(k)) {
                    return;
                }
                if (v instanceof Map<?, ?> nested) {
                    nested.forEach((nk, nv) -> sb.append("  - ").append(nk).append("：").append(nv).append('\n'));
                } else {
                    sb.append("  - ").append(k).append("：").append(v).append('\n');
                }
            });
        }

        // 规格样例，让模型知道型号长什么样
        if (skus != null && !skus.isEmpty()) {
            String sample = skus.stream()
                .map(EcListingSkuVo::getSkuName)
                .filter(n -> n != null && !n.isBlank())
                .limit(8)
                .collect(Collectors.joining("、"));
            sb.append("规格样例：").append(sample).append('\n');
        }

        if (extraHint != null && !extraHint.isBlank()) {
            sb.append("额外要求：").append(extraHint.trim()).append('\n');
        }
        return sb.toString();
    }

    /** 解析模型返回。容忍 ```json 包裹和前后多余文字。 */
    private GenResult parse(String content, String fallbackName) {
        String json = content;
        int s = json.indexOf('{');
        int e = json.lastIndexOf('}');
        if (s >= 0 && e > s) {
            json = json.substring(s, e + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String title = node.path("title").asText("").trim();
            if (title.isEmpty()) {
                throw new ServiceException("AI 返回中没有 title 字段");
            }
            List<String> points = new ArrayList<>();
            JsonNode pn = node.path("points");
            if (pn.isArray()) {
                pn.forEach(p -> {
                    String t = p.asText("").trim();
                    if (!t.isEmpty()) {
                        points.add(t);
                    }
                });
            }
            return new GenResult(title, String.join("\n", points));
        } catch (ServiceException se) {
            throw se;
        } catch (Exception ex) {
            log.warn("AI 标题返回解析失败，商品={} 原文={}", fallbackName, content, ex);
            throw new ServiceException("AI 返回格式无法解析：" + truncate(content, 120));
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "（未填）" : s;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 生成结果 */
    public record GenResult(String title, String sellingPoints) {
    }
}
