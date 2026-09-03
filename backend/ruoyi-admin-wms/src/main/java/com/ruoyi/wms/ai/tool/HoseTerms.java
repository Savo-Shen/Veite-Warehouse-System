package com.ruoyi.wms.ai.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 压油管现场用语 → 系统编码的换算。
 * <p>
 * 胶管 4 位码 = 内径码(2位) + 层数码(2位)，例 1302 = 四分(1/2"，内径 13mm) 二层钢丝。
 * 客户电话里说的是俗称（四分/六分），偶尔说英寸或毫米，这里统一换成内径码。
 *
 * @author Savo
 */
public final class HoseTerms {

    private HoseTerms() {
    }

    /** 俗称 / 英寸 / 内径 mm → 内径码 */
    static final Map<String, String> BORE = new LinkedHashMap<>();

    static {
        put("06", "二分", "2分", "1/4", "1/4\"", "6mm", "6");
        put("08", "二分半", "2分半", "5/16", "5/16\"", "8mm", "8");
        put("10", "三分", "3分", "3/8", "3/8\"", "10mm", "10");
        put("13", "四分", "4分", "1/2", "1/2\"", "13mm", "13", "12mm", "12", "dn12", "dn13");
        put("16", "五分", "5分", "5/8", "5/8\"", "16mm", "16", "dn16");
        put("19", "六分", "6分", "3/4", "3/4\"", "19mm", "19", "dn19", "dn20");
        put("22", "七分", "7分", "7/8", "7/8\"", "22mm", "22", "dn22");
        put("25", "一寸", "1寸", "1\"", "25mm", "25", "dn25");
        put("32", "寸二", "一寸二", "1寸2", "1-1/4", "1-1/4\"", "1.25", "32mm", "32", "dn32");
        put("38", "寸半", "一寸半", "1寸半", "1-1/2", "1-1/2\"", "1.5", "38mm", "38", "dn38", "dn40");
        put("51", "二寸", "2寸", "2\"", "51mm", "51", "dn50", "dn51");
    }

    private static void put(String code, String... keys) {
        for (String k : keys) {
            BORE.put(k.toLowerCase(), code);
        }
    }

    /** 内径码 → 俗称，用于回显 */
    public static final Map<String, String> NICK = Map.ofEntries(
        Map.entry("06", "二分"), Map.entry("08", "二分半"), Map.entry("10", "三分"), Map.entry("13", "四分"),
        Map.entry("16", "五分"), Map.entry("19", "六分"), Map.entry("22", "七分"), Map.entry("25", "一寸"),
        Map.entry("32", "寸二"), Map.entry("38", "寸半"), Map.entry("51", "二寸"));

    /** 通径的各种说法 → 内径码；认不出返回 null */
    public static String boreCode(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase().replace("英寸", "\"").replace("寸\"", "寸").replace("分管", "分").replaceAll("\\s+", "");
        if (t.isEmpty()) {
            return null;
        }
        String direct = BORE.get(t);
        if (direct != null) {
            return direct;
        }
        // 去掉“胶管/油管/管”尾巴再试
        String stripped = t.replaceAll("(胶管|油管|管)$", "");
        direct = BORE.get(stripped);
        if (direct != null) {
            return direct;
        }
        // 2 位内径码本身
        if (stripped.matches("\\d{2}") && NICK.containsKey(stripped)) {
            return stripped;
        }
        return null;
    }

    /** 层数的各种说法 → 层数码；认不出返回 null */
    public static String layerCode(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase().replaceAll("\\s+", "");
        if (t.matches("0[1-6]")) {
            return t;
        }
        if (t.matches("[1-6]")) {
            return "0" + t;
        }
        if (t.contains("一") || t.contains("单")) {
            return "01";
        }
        if (t.contains("二") || t.contains("两") || t.contains("双")) {
            return "02";
        }
        if (t.contains("三")) {
            return "03";
        }
        if (t.contains("四")) {
            return "04";
        }
        if (t.contains("六")) {
            return "06";
        }
        Matcher m = Pattern.compile("(\\d)").matcher(t);
        return m.find() ? "0" + m.group(1) : null;
    }

    private static final Pattern FULL_CODE = Pattern.compile("^\\d{4}$");

    /**
     * 把“四分二层”“1302”“1/2 两层钢丝”这类整句解析成 4 位胶管码；认不出返回 null。
     */
    public static String hoseCode(String hoseCode, String bore, String layers) {
        if (hoseCode != null && FULL_CODE.matcher(hoseCode.trim()).matches()) {
            return hoseCode.trim();
        }
        String text = hoseCode != null ? hoseCode : "";
        String b = boreCode(bore);
        String l = layerCode(layers);
        if ((b == null || l == null) && !text.isBlank()) {
            // 尝试从整句里拆：先找层数词，剩下的当通径
            String t = text.trim();
            Matcher lm = Pattern.compile("([一二两双三四六单\\d])\\s*层").matcher(t);
            if (l == null && lm.find()) {
                l = layerCode(lm.group(1));
                t = t.replace(lm.group(0), "");
            }
            if (b == null) {
                b = boreCode(t.replaceAll("钢丝|编织|缠绕|胶管|油管|管", "").trim());
            }
        }
        if (b == null || l == null) {
            return null;
        }
        return b + l;
    }

    /** 中文数字 → 阿拉伯数字（只处理 1~9，接头叫法里“三分/四分/六分”用） */
    public static String cnDigits(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("一", "1").replace("二", "2").replace("两", "2").replace("三", "3").replace("四", "4")
            .replace("五", "5").replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9");
    }

    /**
     * 接头描述归一化，方便与档案里的 field_name（如“22×1.5 A型芯”“3分英制A型面”）比对：
     * 统一乘号、去空格、大写型号字母、中文数字转阿拉伯。
     */
    public static String normalizeFitting(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().replace('x', '×').replace('X', '×').replace('*', '×').replace('＊', '×')
            .replaceAll("\\s+", "").replace("公头", "芯").replace("母头", "面").replace("外丝", "芯").replace("内丝", "面")
            .replace("直头", "").replace("直的", "").replace("弯头", "弯");
        t = t.replaceAll("^[mM]", "");
        t = cnDigits(t);
        return t.toUpperCase();
    }
}
