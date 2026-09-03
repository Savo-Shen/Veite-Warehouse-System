package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.domain.vo.HoseFerruleVo;
import com.ruoyi.wms.domain.vo.HoseFittingVo;
import com.ruoyi.wms.domain.vo.HosePieceVo;
import com.ruoyi.wms.domain.vo.HoseSpecVo;
import com.ruoyi.wms.service.HoseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：压油管库存——胶管各规格在库的段（长度、库位）、接头档案与库存、外套档案与库存。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class HoseStockTool implements AiTool {

    private final HoseService hoseService;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:hose:list";
    }

    @Override
    public String name() {
        return "hose_stock";
    }

    @Override
    public String title() {
        return "正在查压油管库存";
    }

    @Override
    public String description() {
        return "压油管库存查询：kind=hose 查胶管各规格在库的总米数、段数、最长一段、各段长度和库位（胶管按段存，接单看最长段够不够）；"
            + "kind=fitting 查接头档案和库存（螺纹、A/C/D 型、芯/面、直/弯、数量、库位、进价）；kind=ferrule 查扣压外套。"
            + "当用户问“四分两层还有多少米”“22×1.5 的接头有哪些/还有几个”“油管还剩什么”时调用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "kind", enumOf("hose 胶管（默认），fitting 接头，ferrule 外套", "hose", "fitting", "ferrule"),
            "keyword", string("关键字（可选）：胶管给 四分/1302/两层 等，接头给 22×1.5/3分英制 等"),
            "onlyInStock", bool("只看有库存的（可选，默认 false）")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String kind = text(args, "kind");
        String keyword = text(args, "keyword");
        boolean onlyInStock = flag(args, "onlyInStock");
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();

        if ("fitting".equals(kind)) {
            String kw = keyword == null ? null : HoseTerms.normalizeFitting(keyword).replaceAll("[ACD]型.*$", "");
            List<HoseFittingVo> rows = hoseService.queryFittingOptions(kw);
            if (rows.isEmpty() && kw != null && kw.contains("×")) {
                rows = hoseService.queryFittingOptions("M" + kw);
            }
            for (HoseFittingVo f : rows) {
                if (onlyInStock && (f.getQty() == null || f.getQty() <= 0)) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("编码", f.getFittingSku());
                m.put("名称", f.getFieldName());
                m.put("螺纹", f.getThreadSystem() + " " + f.getThreadSpec());
                m.put("型", f.getSeatType());
                m.put("芯/面", f.getGender());
                m.put("直/弯", f.getAngle());
                m.put("库存", f.getQty() == null ? "未盘" : f.getQty());
                m.put("库位", f.getLocationCode());
                m.put("进价", f.getCostPrice());
                m.put("适配管", f.getBoreHint());
                out.add(m);
            }
            result.put("类别", "接头");
        } else if ("ferrule".equals(kind)) {
            for (HoseFerruleVo f : hoseService.queryFerruleList(keyword)) {
                if (onlyInStock && (f.getQty() == null || f.getQty() <= 0)) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("编码", f.getFerruleSku());
                m.put("名称", f.getFerruleName());
                m.put("适用层数", f.getLayerScope());
                m.put("通径", f.getNickname() + " " + f.getInch());
                m.put("剥皮方式", f.getSkinType());
                m.put("库存", f.getQty() == null ? "未盘" : f.getQty());
                m.put("库位", f.getLocationCode());
                m.put("进价", f.getCostPrice());
                out.add(m);
            }
            result.put("类别", "扣压外套");
        } else {
            // 胶管：规格一共就几十条，全部取回来在 Java 里按“四分/两层/1302/1/2”过滤，
            // 不依赖 mapper 的 keyword 语义（它匹配的不是编码）
            String code = HoseTerms.hoseCode(keyword, keyword, keyword);
            String bore = code != null ? code.substring(0, 2) : HoseTerms.boreCode(keyword);
            String layer = code != null ? code.substring(2) : (keyword != null && keyword.contains("层") ? HoseTerms.layerCode(keyword) : null);
            List<HoseSpecVo> specs = hoseService.querySpecList(null, onlyInStock);
            if (keyword != null) {
                String kwLower = keyword.trim().toLowerCase();
                specs = specs.stream().filter(sp -> {
                    if (bore != null || layer != null) {
                        return (bore == null || bore.equals(sp.getBoreCode())) && (layer == null || layer.equals(sp.getLayerCode()));
                    }
                    String hay = (sp.getHoseCode() + " " + sp.getNickname() + " " + sp.getInch() + " " + sp.getLayerName()).toLowerCase();
                    return hay.contains(kwLower);
                }).toList();
            }
            for (HoseSpecVo s : specs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("胶管码", s.getHoseCode());
                m.put("规格", s.getNickname() + " " + s.getInch() + " " + s.getLayerName());
                m.put("内径mm", s.getIdMm());
                m.put("在库总米数", plain(s.getTotalLengthM()));
                m.put("段数", s.getPieceCount());
                m.put("最长一段", plain(s.getMaxLengthM()));
                m.put("各段", s.getPieceText());
                m.put("库位", s.getLocationNames());
                m.put("进价/米", s.getCostPrice());
                out.add(m);
            }
            if (code != null && specs.size() == 1) {
                List<Map<String, Object>> pieces = new ArrayList<>();
                for (HosePieceVo p : hoseService.queryPieceList(code)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("长度", plain(p.getLengthM()));
                    m.put("状态", p.getStatus());
                    m.put("库位", p.getLocationCode());
                    m.put("pieceId", p.getId());
                    pieces.add(m);
                }
                result.put("各段明细", pieces);
            }
            result.put("类别", "胶管");
            result.put("note", "胶管按段存放，接单要看“最长一段”够不够，不能按总米数算；余料接不上");
        }
        result.put("count", out.size());
        result.put("items", out);
        if (out.isEmpty()) {
            result.put("hint", "没有匹配的记录，可换关键字或去掉 onlyInStock");
        }
        return objectMapper.writeValueAsString(result);
    }
}
