package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.service.ItemSkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具：按关键字搜索商品/规格，返回名称、编号、条码、售价、进价。
 * 用于“查商品价格 / 确认商品是否存在 / 拿到 skuId 以便后续建单”。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class SearchSkuTool implements AiTool {

    private final ItemSkuService itemSkuService;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:item:list";
    }

    @Override
    public String name() {
        return "search_sku";
    }

    @Override
    public String description() {
        return "根据关键字搜索商品/规格，返回商品名称、规格名称、商品编号、规格编号、条码、售价、进价。"
            + "当用户问某个商品的价格、是否有这个商品、或需要先定位到具体商品时调用。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "商品或规格的名称、编号、条码关键字，可多个词用空格分隔");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", keyword);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("keyword"));
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String keyword = args.path("keyword").asText("");

        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(1);
        pageQuery.setPageSize(10);

        List<ItemSkuMapVo> rows = itemSkuService.queryPageList(new ItemSkuBo(), pageQuery, keyword).getRows();

        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemSkuMapVo row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("商品", row.getItem() == null ? null : row.getItem().getItemName());
            m.put("商品编号", row.getItem() == null ? null : row.getItem().getItemCode());
            if (row.getItemSku() != null) {
                m.put("规格", row.getItemSku().getSkuName());
                m.put("规格编号", row.getItemSku().getSkuCode());
                m.put("条码", row.getItemSku().getBarcode());
                m.put("售价", row.getItemSku().getSellingPrice());
                m.put("进价", row.getItemSku().getCostPrice());
                m.put("skuId", row.getItemSku().getId());
            }
            out.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("items", out);
        if (out.isEmpty()) {
            result.put("hint", "未匹配到全部关键词（搜索按词全部命中）。可只用其中一个词重试，"
                + "例如只用尺寸“" + keyword.replaceAll("[\\u4e00-\\u9fff]+", "").trim()
                + "”、或只用商品名再搜；注意系统按商品名/规格/编号/条码精确包含匹配，俗称可能与登记名不同。");
        }
        return objectMapper.writeValueAsString(result);
    }
}
