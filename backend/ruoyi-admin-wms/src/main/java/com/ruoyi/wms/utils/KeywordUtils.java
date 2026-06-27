package com.ruoyi.wms.utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 综合搜索关键字处理工具
 * <p>
 * 用于将用户在“综合搜索”框中输入的关键字拆分成多个词，
 * 支持以任意空白（含中文全角空格 　）分隔，多个词之间为“与”关系、
 * 每个词在多个字段间为“或”关系。
 *
 * @author Savo
 */
public class KeywordUtils {

    /** 空白分隔符：半角空白、制表符、换行以及中文全角空格 */
    private static final String WHITESPACE_REGEX = "[\\s\\u3000]+";

    /** 中文(CJK)字符范围 */
    private static final String CJK = "\\u4e00-\\u9fff";

    /**
     * 商品俗称/别名词表。
     * <p>
     * 搜索时组内为“或”关系，例如用户输入“PU管 10*6.5”时会匹配
     * {@code (PU管 OR pu管 OR 气管) AND 10*6.5}。
     */
    private static final Map<String, List<String>> SYNONYMS = Map.of(
        "PU管", List.of("PU管", "pu管", "PU气管", "pu气管", "气管"),
        "pu管", List.of("pu管", "PU管", "PU气管", "pu气管", "气管"),
        "PU气管", List.of("PU气管", "pu气管", "PU管", "pu管", "气管"),
        "pu气管", List.of("pu气管", "PU气管", "PU管", "pu管", "气管"),
        "气管", List.of("气管", "PU管", "pu管", "PU气管", "pu气管")
    );

    /**
     * 将综合搜索关键字按空白拆分为多个词，自动去除首尾空白与空词。
     * <p>
     * 同时在“中文 ↔ 非中文”边界自动插入空格，于是 {@code "10*6.5气管"} 会被拆成
     * {@code ["10*6.5", "气管"]}——这样商品名(气管)与规格(10*6.5)分别命中不同字段也能搜到。
     * 注意：不拆分 {@code *} 等符号本身，"10*6.5" 仍是一个整体词。
     *
     * @param keyword 原始关键字，可为 null
     * @return 拆分后的词列表，关键字为空时返回空列表（不会为 null）
     */
    public static List<String> splitWords(String keyword) {
        if (keyword == null) {
            return Collections.emptyList();
        }
        String trimmed = segment(keyword.trim());
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(trimmed.split(WHITESPACE_REGEX))
            .filter(word -> !word.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * 将关键字拆成“词组”。外层多个组之间是 AND，内层同义词之间是 OR。
     *
     * @param keyword 原始关键字，可为 null
     * @return 关键字词组，关键字为空时返回空列表
     */
    public static List<List<String>> splitWordGroups(String keyword) {
        List<String> words = splitWords(keyword);
        if (words.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<String>> groups = new ArrayList<>();
        for (String word : words) {
            groups.add(expandWord(word));
        }
        return groups;
    }

    /**
     * 展开单个词的同义词。没有配置别名时返回自身。
     */
    public static List<String> expandWord(String word) {
        if (word == null || word.isBlank()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        expanded.add(word);
        List<String> synonyms = SYNONYMS.get(word);
        if (synonyms != null) {
            expanded.addAll(synonyms);
        }
        return new ArrayList<>(expanded);
    }

    /** 在中文与非中文的交界处插入空格，便于按词拆分。 */
    private static String segment(String s) {
        // 非中文(且非空白)之后紧跟中文 → 插空格，如 "5气" -> "5 气"
        s = s.replaceAll("(?<=[^" + CJK + "\\s])(?=[" + CJK + "])", " ");
        // 中文之后紧跟非中文(且非空白) → 插空格，如 "管10" -> "管 10"
        s = s.replaceAll("(?<=[" + CJK + "])(?=[^" + CJK + "\\s])", " ");
        return s;
    }

    /**
     * 归一化关键字：去除首尾空白、把连续空白（含全角空格）合并为单个半角空格。
     * <p>
     * 供 MyBatis XML 中使用 {@code split(' ')} 拆分的查询复用，避免产生空词。
     *
     * @param keyword 原始关键字，可为 null
     * @return 归一化后的字符串；关键字为空时返回 null（便于 {@code <if>} 判空）
     */
    public static String normalize(String keyword) {
        List<String> words = splitWords(keyword);
        return words.isEmpty() ? null : String.join(" ", words);
    }
}
