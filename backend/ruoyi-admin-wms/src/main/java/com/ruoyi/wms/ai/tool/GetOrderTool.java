package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.ai.tool.DraftSupport.SourceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：按单号调出一张出库单/入库单的完整内容（表头 + 每行明细、单价、当前售价/进价）。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class GetOrderTool implements AiTool {

    private final DraftSupport support;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> permissionsUsed() {
        return List.of(DraftSupport.PERM_SHIPMENT, DraftSupport.PERM_RECEIPT);
    }

    @Override
    public String name() {
        return "get_order";
    }

    @Override
    public String title() {
        return "正在调出单据";
    }

    @Override
    public String description() {
        return "按单号查看一张出库单（CK 开头）或入库单（RK 开头）的完整内容：客户/供应商、仓库、日期、状态、"
            + "每一行的商品、规格、数量、单价、金额，以及该商品当前登记的售价和进价、明细是否还能修改。"
            + "当用户提到某个单号、要看/核对/复制/修改某张单时先调用它。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "orderNo", string("单号，如 CK08054357 或 RK08186356；只记得后几位也可以"),
            "orderType", enumOf("单据类型（可选）：shipment 出库单，receipt 入库单。不给则先找出库单再找入库单", "shipment", "receipt")
        ), List.of("orderNo"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String orderNo = text(args, "orderNo");
        if (orderNo == null) {
            return "{\"error\":\"orderNo 不能为空\"}";
        }
        SourceOrder src = support.loadOrder(orderNo, text(args, "orderType"));
        if (src == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("error", "未找到单号“" + orderNo + "”（或当前账号没有权限查看这类单据）");
            m.put("hint", "可用 query_orders 按客户/供应商/商品/日期找到准确单号");
            return objectMapper.writeValueAsString(m);
        }
        return objectMapper.writeValueAsString(support.describe(src, true));
    }
}
