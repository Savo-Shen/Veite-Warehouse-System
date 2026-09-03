package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.ai.tool.DraftSupport.SkuPick;
import com.ruoyi.wms.domain.entity.Location;
import com.ruoyi.wms.domain.vo.LocationVo;
import com.ruoyi.wms.mapper.LocationMapper;
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
 * 工具：生成「库位调整」操作草稿——把商品规格的存放库位改到另一个货架格子。
 * <p>
 * 这是“动作草稿”（type=action）：不预填表单，而是在对话里给用户一个“确认执行”按钮，
 * 点了之后由 {@code AiActionService} 真正修改。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class RelocateSkuDraftTool implements AiTool {

    private final DraftSupport support;
    private final LocationMapper locationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:item:edit";
    }

    @Override
    public String name() {
        return "relocate_sku_draft";
    }

    @Override
    public String title() {
        return "正在整理库位调整";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "把商品规格的存放库位（货架格子编码，如 1-A1-3、2-B2-2，或库位名称）改到另一个库位。"
            + "生成一个待确认的操作，用户在对话里点“确认执行”后才真正修改，不会直接改。"
            + "当用户说“把 X 挪到/放到/改到 某库位”“X 现在放在 2-B2-2 了”“X 的库位改成 1-A2-1”时调用。"
            + "这不是仓库之间的移库单（那个用 create_movement_draft）。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> item = object(props(
            "name", string("商品/规格关键字（已知 skuId 时可省略）"),
            "skuId", number("商品规格 id（可选）")
        ), List.of());
        return object(props(
            "items", array("要换库位的商品（可多个）", item),
            "location", string("目标库位的编码或名称，如 2-B2-2、油管接头")
        ), List.of("items", "location"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String locText = text(args, "location");
        if (locText == null) {
            return error("location 不能为空");
        }
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> unresolved = new ArrayList<>();

        // 库位：先精确编码，再模糊编码/名称
        List<LocationVo> exact = locationMapper.selectVoList(Wrappers.<Location>lambdaQuery()
            .eq(Location::getLocationCode, locText));
        List<LocationVo> hits = exact.isEmpty()
            ? locationMapper.selectVoList(Wrappers.<Location>lambdaQuery()
                .like(Location::getLocationCode, locText).or().like(Location::getLocationName, locText)
                .last("limit 10"))
            : exact;
        if (hits.isEmpty()) {
            List<LocationVo> some = locationMapper.selectVoList(Wrappers.<Location>lambdaQuery()
                .orderByAsc(Location::getLocationCode).last("limit 15"));
            return error("没有叫“" + locText + "”的库位。库位编码形如 1-A1-3（区-排列-层），现有的例如："
                + some.stream().map(l -> l.getLocationCode() + " " + l.getLocationName()).collect(Collectors.joining("、")));
        }
        LocationVo loc = hits.get(0);
        if (hits.size() > 1) {
            warnings.add("库位“" + locText + "”匹配到多个（" + hits.stream().map(LocationVo::getLocationCode)
                .collect(Collectors.joining("、")) + "），已选 " + loc.getLocationCode() + "，请确认。");
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        List<Long> skuIds = new ArrayList<>();
        JsonNode items = args.path("items");
        if (items.isArray()) {
            for (JsonNode it : items) {
                String name = text(it, "name");
                BigDecimal skuIdNum = decimal(it, "skuId");
                Long skuId = skuIdNum == null ? null : skuIdNum.longValue();
                if (name == null && skuId == null) {
                    continue;
                }
                SkuPick pick = support.resolveSku(skuId, name, null);
                if (!pick.found()) {
                    unresolved.add(Map.of("name", name != null ? name : ("skuId " + skuId), "reason", pick.reason()));
                    continue;
                }
                if (pick.warning() != null) {
                    warnings.add(pick.warning());
                }
                Long sid = pick.picked().getItemSku().getId();
                String fromCode = "（未设置）";
                String cur = pick.picked().getItemSku().getItemLocationId();
                if (cur != null && !cur.isBlank()) {
                    try {
                        LocationVo from = locationMapper.selectVoById(Long.valueOf(cur.trim()));
                        fromCode = from == null ? "（库位已不存在）" : from.getLocationCode();
                    } catch (NumberFormatException e) {
                        fromCode = cur;
                    }
                }
                if (String.valueOf(loc.getId()).equals(cur)) {
                    warnings.add("“" + DraftSupport.shortName(pick.picked()) + "”本来就在 " + loc.getLocationCode() + "，无需调整。");
                    continue;
                }
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("skuId", sid);
                line.put("商品", pick.picked().getItem() == null ? null : pick.picked().getItem().getItemName());
                line.put("规格", pick.picked().getItemSku().getSkuName());
                line.put("原库位", fromCode);
                line.put("新库位", loc.getLocationCode());
                lines.add(line);
                skuIds.add(sid);
            }
        }
        if (lines.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("error", unresolved.isEmpty() ? "所有商品本来就在 " + loc.getLocationCode() + "，无需调整" : "没有匹配到需要调整的商品");
            if (!warnings.isEmpty()) {
                m.put("warnings", warnings);
            }
            if (!unresolved.isEmpty()) {
                m.put("unresolved", unresolved);
            }
            return objectMapper.writeValueAsString(m);
        }

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "action");
        draft.put("action", "relocate_sku");
        draft.put("title", "库位调整 → " + loc.getLocationCode() + " " + (loc.getLocationName() == null ? "" : loc.getLocationName()));
        draft.put("location", Map.of("id", loc.getId(), "code", loc.getLocationCode(), "name", loc.getLocationName() == null ? "" : loc.getLocationName()));
        draft.put("lines", lines);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("locationId", loc.getId());
        payload.put("locationCode", loc.getLocationCode());
        payload.put("skuIds", skuIds);
        draft.put("payload", payload);
        draft.put("unresolved", unresolved);
        draft.put("warnings", warnings);
        draft.put("executed", false);
        draft.put("说明", "这是待确认操作，用户在对话里点“确认执行”后才会修改库位；告诉用户核对后点按钮即可。");
        return objectMapper.writeValueAsString(draft);
    }

    private String error(String msg) throws Exception {
        return objectMapper.writeValueAsString(Map.of("error", msg));
    }
}
