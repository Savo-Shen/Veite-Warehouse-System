package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.domain.entity.Merchant;
import com.ruoyi.wms.domain.vo.MerchantVo;
import com.ruoyi.wms.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：查往来单位（客户/供应商/物流单位）的名称、编号、联系人、电话、地址。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class SearchMerchantTool implements AiTool {

    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:merchant:list";
    }

    @Override
    public String name() {
        return "search_merchant";
    }

    @Override
    public String title() {
        return "正在查往来单位";
    }

    @Override
    public String description() {
        return "查往来单位（客户、供应商、物流单位）：按名称/编号/联系人/电话关键字搜索，返回名称、编号、类型、联系人、手机、座机、地址。"
            + "当用户问某客户的电话/地址/联系人、有没有这个客户、或需要确认客户全称时调用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "keyword", string("名称、编号、联系人或电话的关键字（可选，留空列出前若干个）"),
            "type", enumOf("类型过滤（可选）：customer 客户，supplier 供应商，logistics 物流单位", "customer", "supplier", "logistics"),
            "limit", integer("最多返回条数，默认 20，最大 50")
        ), List.of());
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String keyword = text(args, "keyword");
        String type = text(args, "type");
        Integer merchantType = switch (type == null ? "" : type) {
            case "customer" -> 1;
            case "supplier" -> 2;
            case "logistics" -> 3;
            default -> null;
        };
        int limit = limit(args, "limit", 20, 50);

        var lqw = Wrappers.<Merchant>lambdaQuery();
        if (keyword != null) {
            lqw.and(w -> w.like(Merchant::getMerchantName, keyword)
                .or().like(Merchant::getMerchantCode, keyword)
                .or().like(Merchant::getContactPerson, keyword)
                .or().like(Merchant::getMobile, keyword)
                .or().like(Merchant::getTel, keyword));
        }
        lqw.eq(merchantType != null, Merchant::getMerchantType, merchantType);
        lqw.orderByAsc(Merchant::getMerchantCode);
        lqw.last("limit " + limit);
        List<MerchantVo> rows = merchantMapper.selectVoList(lqw);

        List<Map<String, Object>> out = new ArrayList<>();
        for (MerchantVo m : rows) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("名称", DraftSupport.clean(m.getMerchantName()));
            o.put("编号", m.getMerchantCode());
            o.put("类型", merchantType(m.getMerchantType()));
            o.put("联系人", DraftSupport.clean(m.getContactPerson()));
            o.put("手机", DraftSupport.clean(m.getMobile()));
            o.put("座机", DraftSupport.clean(m.getTel()));
            o.put("地址", DraftSupport.clean(m.getAddress()));
            o.put("备注", DraftSupport.clean(m.getRemark()));
            o.put("merchantId", m.getId());
            out.add(o);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("items", out);
        if (out.isEmpty()) {
            result.put("hint", "没有匹配的往来单位，可换更短的关键字（比如只用名字里的两个字）再试");
        }
        return objectMapper.writeValueAsString(result);
    }
}
