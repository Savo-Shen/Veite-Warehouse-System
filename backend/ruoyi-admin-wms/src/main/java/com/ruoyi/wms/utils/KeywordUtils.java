package com.ruoyi.wms.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    /**
     * 将综合搜索关键字按空白拆分为多个词，自动去除首尾空白与空词。
     *
     * @param keyword 原始关键字，可为 null
     * @return 拆分后的词列表，关键字为空时返回空列表（不会为 null）
     */
    public static List<String> splitWords(String keyword) {
        if (keyword == null) {
            return Collections.emptyList();
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(trimmed.split(WHITESPACE_REGEX))
            .filter(word -> !word.isEmpty())
            .collect(Collectors.toList());
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
