package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.domain.entity.Merchant;
import com.ruoyi.wms.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：新建往来单位（客户/供应商/物流单位）的建档草稿。动作草稿，用户点“确认执行”才写入；编号在执行时自动生成。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class CreateMerchantDraftTool implements AiTool {

    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:merchant:edit";
    }

    @Override
    public String name() {
        return "create_merchant_draft";
    }

    @Override
    public String title() {
        return "正在整理往来单位建档";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "新建客户/供应商/物流单位的建档草稿：整理名称、类型、联系人、电话、地址，生成待确认操作，"
            + "用户在对话里点“确认执行”后才写入（编号自动生成）。当用户说“新建客户 X，电话 xx”“加一个供应商”时调用。"
            + "如果只是查有没有这个单位，用 search_merchant。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "name", string("单位全称"),
            "type", enumOf("类型：customer 客户，supplier 供应商，logistics 物流单位", "customer", "supplier", "logistics"),
            "contactPerson", string("联系人（可选）"),
            "mobile", string("手机号（可选）"),
            "tel", string("座机（可选）"),
            "address", string("地址（可选）"),
            "remark", string("备注（可选）")
        ), List.of("name", "type"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String name = text(args, "name");
        String type = text(args, "type");
        if (name == null || type == null) {
            return objectMapper.writeValueAsString(Map.of("error", "name 和 type 不能为空"));
        }
        Integer merchantType = switch (type) {
            case "customer" -> 1;
            case "supplier" -> 2;
            case "logistics" -> 3;
            default -> null;
        };
        if (merchantType == null) {
            return objectMapper.writeValueAsString(Map.of("error", "type 只能是 customer/supplier/logistics"));
        }
        List<String> warnings = new ArrayList<>();
        List<Merchant> exact = merchantMapper.selectList(Wrappers.<Merchant>lambdaQuery().eq(Merchant::getMerchantName, name));
        if (!exact.isEmpty()) {
            Merchant m = exact.get(0);
            return objectMapper.writeValueAsString(Map.of(
                "error", "已经有叫“" + name + "”的往来单位（编号 " + m.getMerchantCode() + "，" + merchantType(m.getMerchantType()) + "），不用重复建档",
                "merchantId", m.getId()));
        }
        String core = name.replaceAll("(有限公司|有限责任公司|公司|厂|店)$", "");
        if (core.length() >= 2) {
            List<Merchant> similar = merchantMapper.selectList(Wrappers.<Merchant>lambdaQuery()
                .like(Merchant::getMerchantName, core).last("limit 5"));
            if (!similar.isEmpty()) {
                warnings.add("已有名称相近的单位：" + similar.stream()
                    .map(m -> DraftSupport.clean(m.getMerchantName()) + "（" + merchantType(m.getMerchantType()) + "）")
                    .collect(Collectors.joining("、")) + "，请确认不是同一家。");
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantName", name);
        payload.put("merchantType", merchantType);
        payload.put("contactPerson", text(args, "contactPerson"));
        payload.put("mobile", text(args, "mobile"));
        payload.put("tel", text(args, "tel"));
        payload.put("address", text(args, "address"));
        payload.put("remark", text(args, "remark"));

        Map<String, Object> line = new LinkedHashMap<>();
        line.put("名称", name);
        line.put("类型", merchantType(merchantType));
        line.put("联系人", payload.get("contactPerson"));
        line.put("手机", payload.get("mobile"));
        line.put("座机", payload.get("tel"));
        line.put("地址", payload.get("address"));
        line.put("备注", payload.get("remark"));

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "action");
        draft.put("action", "create_merchant");
        draft.put("title", "新建" + merchantType(merchantType) + "：" + name);
        draft.put("lines", List.of(line));
        draft.put("payload", payload);
        draft.put("warnings", warnings);
        draft.put("executed", false);
        draft.put("说明", "待确认操作：用户在对话里点“确认执行”后才会建档，编号届时自动生成。");
        return objectMapper.writeValueAsString(draft);
    }
}
