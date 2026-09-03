package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具实现共用的小工具：JSON Schema 拼装、入参取值、金额/日期格式化。
 * <p>
 * 每个工具原来各自手写一遍 LinkedHashMap 拼 schema，工具一多就全是样板代码，这里收拢。
 *
 * @author Savo
 */
public final class ToolSupport {

    private ToolSupport() {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /* ---------- JSON Schema ---------- */

    public static Map<String, Object> string(String description) {
        return prop("string", description);
    }

    public static Map<String, Object> number(String description) {
        return prop("number", description);
    }

    public static Map<String, Object> integer(String description) {
        return prop("integer", description);
    }

    public static Map<String, Object> bool(String description) {
        return prop("boolean", description);
    }

    public static Map<String, Object> enumOf(String description, String... values) {
        Map<String, Object> p = prop("string", description);
        p.put("enum", Arrays.asList(values));
        return p;
    }

    public static Map<String, Object> array(String description, Map<String, Object> items) {
        Map<String, Object> p = prop("array", description);
        p.put("items", items);
        return p;
    }

    /** properties 按传入顺序排列；required 可为空 */
    public static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /** 保序的 properties 构造器：props("a", schemaA, "b", schemaB, ...) */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> props(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], (Map<String, Object>) kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        if (description != null) {
            p.put("description", description);
        }
        return p;
    }

    /* ---------- 入参取值 ---------- */

    /** 取字符串字段；缺失、null、空白都返回 null */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText(null);
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 取数字字段；模型偶尔会把数字放在字符串里（"3"、"1,192.8"），一并兼容 */
    public static BigDecimal decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        String s = v.asText("").replace(",", "").replace("，", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer integer(JsonNode node, String field) {
        BigDecimal d = decimal(node, field);
        return d == null ? null : d.intValue();
    }

    public static boolean flag(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        JsonNode v = node.path(field);
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        String s = v.asText("");
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "是".equals(s);
    }

    /** 分页条数：缺省 def，最多 max */
    public static int limit(JsonNode node, String field, int def, int max) {
        Integer n = integer(node, field);
        if (n == null || n <= 0) {
            return def;
        }
        return Math.min(n, max);
    }

    /** 解析 yyyy-MM-dd；解析失败返回 null（由调用方决定是否提示） */
    public static LocalDate date(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        s = s.replace('/', '-').replace('.', '-');
        try {
            return LocalDate.parse(s, DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /* ---------- 输出格式化 ---------- */

    public static String fmt(LocalDate d) {
        return d == null ? null : d.format(DATE);
    }

    public static String fmt(LocalDateTime d) {
        return d == null ? null : d.format(DATE_TIME);
    }

    public static String fmt(java.util.Date d) {
        if (d == null) {
            return null;
        }
        return fmt(LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault()));
    }

    /** 去掉多余的小数零：3.000 → 3，1.50 → 1.5 */
    public static BigDecimal plain(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** 单价 = 金额 / 数量（保留两位），数量为 0 或缺失时返回 null */
    public static BigDecimal unitPrice(BigDecimal amount, BigDecimal quantity) {
        if (amount == null || quantity == null || quantity.signum() == 0) {
            return null;
        }
        return amount.divide(quantity, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static String shipmentStatus(Integer s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case 1 -> "已出库";
            case 0 -> "未出库";
            case -1 -> "已作废";
            default -> String.valueOf(s);
        };
    }

    public static String receiptStatus(Integer s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case 1 -> "已入库";
            case 0 -> "未入库";
            case -1 -> "已作废";
            default -> String.valueOf(s);
        };
    }

    public static String shipmentType(Long t) {
        if (t == null) {
            return "";
        }
        return switch (t.intValue()) {
            case 1 -> "退货出库";
            case 2 -> "销售出库";
            case 3 -> "生产出库";
            default -> String.valueOf(t);
        };
    }

    public static String receiptType(Long t) {
        if (t == null) {
            return "";
        }
        return switch (t.intValue()) {
            case 1 -> "生产入库";
            case 2 -> "采购入库";
            case 3 -> "退货入库";
            case 4 -> "归还入库";
            default -> String.valueOf(t);
        };
    }

    public static String merchantType(Integer t) {
        if (t == null) {
            return "";
        }
        return switch (t) {
            case 1 -> "客户";
            case 2 -> "供应商";
            case 3 -> "物流单位";
            default -> String.valueOf(t);
        };
    }
}
