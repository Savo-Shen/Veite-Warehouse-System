package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.wms.domain.bo.HoseQuoteBo;
import com.ruoyi.wms.domain.vo.HoseFittingVo;
import com.ruoyi.wms.domain.vo.HoseQuoteLineVo;
import com.ruoyi.wms.domain.vo.HoseQuoteVo;
import com.ruoyi.wms.service.HoseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：压油管配料与报价。客户一句“四分两层 1.2 米，两头 22×1.5 A 型芯”→ 换成系统编码 → 调 {@link HoseService#quote}。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class HoseQuoteTool implements AiTool {

    private final HoseService hoseService;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:hose:list";
    }

    @Override
    public String name() {
        return "hose_quote";
    }

    @Override
    public String title() {
        return "正在算压油管配料和报价";
    }

    @Override
    public String description() {
        return "压油管（液压胶管总成）配料与报价：给通径、层数、长度和两端接头，算出胶管有没有够长的段、接头和外套够不够、"
            + "在哪个库位、成本和售价、能不能现场压。当用户说“接一根四分两层 1米2 两头 22×1.5 A型芯”“压一根六分三层的油管”"
            + "“四分管配 3分英制 A型面 多少钱”时调用。通径按用户原话给（四分/六分/1/2/13 都行），层数给数字或“两层”，"
            + "接头按用户原话给（如 22×1.5 A型芯、3分英制A型面、18×1.5 C型弯面），工具会自己换算和匹配档案。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "hoseCode", string("胶管 4 位码（可选），如 1302；知道就直接给，不知道给 bore + layers"),
            "bore", string("通径，按用户原话：四分/六分/一寸/寸二、或 1/2、3/8、或内径 13/19"),
            "layers", string("钢丝层数：1/2/3/4，或“两层/三层”"),
            "lengthM", number("单根长度，米（1米2 = 1.2）"),
            "endA", string("A 端接头，按用户原话，如“22×1.5 A型芯”“3分英制A型面”"),
            "endB", string("B 端接头（可选，不给则与 A 端同款）"),
            "skinType", enumOf("外套类型（可选）：非剥皮（默认）/剥皮", "非剥皮", "剥皮"),
            "assemblyQty", integer("要压几根，默认 1"),
            "withSteps", bool("是否要压管操作步骤（默认不要，用户问怎么压/参数时给 true）")
        ), List.of("lengthM"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String code = HoseTerms.hoseCode(text(args, "hoseCode"), text(args, "bore"), text(args, "layers"));
        if (code == null) {
            return error("没认出胶管规格：需要通径（四分/六分/1/2…）和层数（一层/两层/三层/四层）。"
                + "现有通径：二分06 二分半08 三分10 四分13 五分16 六分19 七分22 一寸25 寸二32 寸半38 二寸51；层数码 01~04。");
        }
        BigDecimal length = decimal(args, "lengthM");
        if (length == null || length.signum() <= 0) {
            return error("需要长度（米），例如 1.2");
        }
        List<String> notes = new ArrayList<>();
        String endA = text(args, "endA");
        String endB = text(args, "endB");
        String skuA = null;
        String skuB = null;
        if (endA != null) {
            FittingMatch fa = matchFitting(endA);
            if (fa.error != null) {
                return error(fa.error);
            }
            skuA = fa.sku;
            if (fa.note != null) {
                notes.add(fa.note);
            }
        }
        if (endB != null) {
            FittingMatch fb = matchFitting(endB);
            if (fb.error != null) {
                return error(fb.error);
            }
            skuB = fb.sku;
            if (fb.note != null) {
                notes.add(fb.note);
            }
        }

        HoseQuoteBo bo = new HoseQuoteBo();
        bo.setHoseCode(code);
        bo.setLengthM(length);
        bo.setEndASku(skuA);
        bo.setEndBSku(skuB);
        bo.setSkinType(text(args, "skinType"));
        Integer qty = integer(args, "assemblyQty");
        bo.setAssemblyQty(qty == null || qty < 1 ? 1 : qty);

        HoseQuoteVo vo;
        try {
            vo = hoseService.quote(bo);
        } catch (ServiceException e) {
            return error(e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("胶管码", code);
        out.put("规格", HoseTerms.NICK.getOrDefault(code.substring(0, 2), code.substring(0, 2)) + " " + code.substring(2) + "层");
        out.put("结论", vo.getVerdict());
        out.put("一句话", vo.getSummary());
        out.put("可现场压", vo.getCanCrimp());
        out.put("售价", vo.getSellPrice());
        out.put("成本合计", vo.getCostTotal());
        out.put("加价倍数", vo.getSellMarkup());
        if (vo.getMissingCostCount() != null && vo.getMissingCostCount() > 0) {
            out.put("未录价项数", vo.getMissingCostCount());
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (HoseQuoteLineVo l : vo.getLines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("项目", l.getRole());
            m.put("编码", l.getCode());
            m.put("名称", l.getName());
            m.put("规格", l.getSpec());
            m.put("需要", l.getNeedText());
            m.put("库存", l.getStockText());
            m.put("库位", l.getLocationText());
            m.put("状态", l.getStatus());
            m.put("单价成本", l.getUnitCost());
            m.put("金额", l.getAmount());
            lines.add(m);
        }
        out.put("配料", lines);
        if (!vo.getBlockers().isEmpty()) {
            out.put("阻碍", vo.getBlockers());
        }
        List<String> warnings = new ArrayList<>(notes);
        warnings.addAll(vo.getWarnings());
        if (!warnings.isEmpty()) {
            out.put("warnings", warnings);
        }
        if (vo.getCrimp() != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("模具号", vo.getCrimp().getDieNo());
            c.put("扣压直径mm", vo.getCrimp().getCrimpDiameterMm());
            c.put("剥胶长度mm", vo.getCrimp().getStripLengthMm());
            c.put("插入深度mm", vo.getCrimp().getInsertDepthMm());
            c.put("压机档位", vo.getCrimp().getPressGear());
            c.put("店里能压", vo.getCrimp().getShopCanCrimp());
            out.put("扣压参数", c);
        }
        if (flag(args, "withSteps")) {
            out.put("操作步骤", vo.getSteps());
        }
        out.put("note", "售价 = 成本 × 加价倍数并取整到元；进价/成本只给员工看，回复客户时只说售价。");
        return objectMapper.writeValueAsString(out);
    }

    private record FittingMatch(String sku, String note, String error) {
    }

    /**
     * 用户原话 → 接头档案 SKU。先按螺纹关键字捞候选，再按 型(A/C/D)、芯/面、直/弯 过滤。
     */
    private FittingMatch matchFitting(String textIn) {
        String norm = HoseTerms.normalizeFitting(textIn);
        // 螺纹关键字：22×1.5 / G3/8 / 3分 之类
        String thread = null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?×\\d+(?:\\.\\d+)?)").matcher(norm);
        if (m.find()) {
            thread = m.group(1);
        } else {
            java.util.regex.Matcher f = java.util.regex.Pattern.compile("(\\d+)分").matcher(norm);
            if (f.find()) {
                thread = f.group(1) + "分";
            } else {
                java.util.regex.Matcher g = java.util.regex.Pattern.compile("([GJ]I?C?\\d+/?\\d*)").matcher(norm);
                if (g.find()) {
                    thread = g.group(1);
                }
            }
        }
        String keyword = thread != null ? thread : textIn.trim();
        List<HoseFittingVo> options = hoseService.queryFittingOptions(keyword);
        if (options.isEmpty() && thread != null && thread.contains("×")) {
            options = hoseService.queryFittingOptions("M" + thread);
        }
        if (options.isEmpty()) {
            return new FittingMatch(null, null, "接头档案里没有“" + textIn + "”。接头写法示例：22×1.5 A型芯、18×1.5 C型弯面、3分英制A型面、3分美制D型芯。"
                + "可用 hose_stock(kind=fitting, keyword=…) 查有哪些。");
        }
        // 型
        String seat = null;
        java.util.regex.Matcher s = java.util.regex.Pattern.compile("([ACD])型?").matcher(norm.replaceAll("[GJ]I?C?\\d+/?\\d*", ""));
        if (s.find()) {
            seat = s.group(1);
        }
        String gender = norm.contains("芯") ? "芯" : (norm.contains("面") ? "面" : null);
        String angle = norm.contains("弯") ? "弯" : null;
        // 螺纹体系
        String system = norm.contains("英制") ? "英制" : norm.contains("美制") ? "美制" : (norm.contains("公制") ? "公制" : null);

        List<HoseFittingVo> cands = options;
        if (seat != null) {
            String fs = seat;
            cands = cands.stream().filter(o -> fs.equalsIgnoreCase(o.getSeatType())).toList();
        }
        if (gender != null) {
            String fg = gender;
            cands = cands.stream().filter(o -> fg.equals(o.getGender())).toList();
        }
        if (system != null) {
            String fsys = system;
            cands = cands.stream().filter(o -> fsys.equals(o.getThreadSystem())).toList();
        }
        // 没说弯就当直头
        String fa = angle == null ? "直" : angle;
        List<HoseFittingVo> byAngle = cands.stream().filter(o -> fa.equals(o.getAngle())).toList();
        if (!byAngle.isEmpty()) {
            cands = byAngle;
        }
        // 精确名字命中优先
        for (HoseFittingVo o : cands) {
            if (HoseTerms.normalizeFitting(o.getFieldName()).equals(norm)) {
                return new FittingMatch(o.getFittingSku(), null, null);
            }
        }
        if (cands.size() == 1) {
            HoseFittingVo o = cands.get(0);
            return new FittingMatch(o.getFittingSku(), "接头“" + textIn + "”按档案匹配为 " + o.getFieldName() + "（" + o.getFittingSku() + "）", null);
        }
        String list = (cands.isEmpty() ? options : cands).stream().limit(8)
            .map(o -> o.getFieldName() + "（" + o.getFittingSku() + "）").collect(Collectors.joining("、"));
        return new FittingMatch(null, null, "接头“" + textIn + "”不够明确，候选有：" + list
            + "。请补充：A/C/D 型、芯（公头）还是面（母头）、直头还是弯头。");
    }

    private String error(String msg) throws Exception {
        return objectMapper.writeValueAsString(Map.of("error", msg));
    }
}
