package com.ruoyi.wms.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.vo.ItemSkuMapVo;
import com.ruoyi.wms.service.ItemSkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：按关键字搜索商品/规格，返回名称、编号、条码、售价、进价、单位和 skuId。
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
    public String title() {
        return "正在查商品";
    }

    @Override
    public String description() {
        return "根据关键字搜索商品/规格，返回商品名称、规格、编号、条码、计量单位、售价、进价和 skuId。"
            + "当用户问某个商品的价格、有没有这个商品、或需要先定位到具体商品（拿 skuId 建单）时调用。"
            + "多个关键词用空格分隔，词之间是“且”的关系；同一商品可能有“按米”和“按卷/捆”两条记录，看单位区分。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return object(props(
            "keyword", string("商品或规格的名称、编号、条码关键字，多个词用空格分隔，如“气管 10*6.5”"),
            "limit", integer("最多返回条数，默认 10，最大 50")
        ), List.of("keyword"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String keyword = text(args, "keyword");
        if (keyword == null) {
            return "{\"error\":\"keyword 不能为空\"}";
        }
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(1);
        pageQuery.setPageSize(limit(args, "limit", 10, 50));

        TableDataInfo<ItemSkuMapVo> page = itemSkuService.queryPageList(new ItemSkuBo(), pageQuery, keyword);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemSkuMapVo row : page.getRows()) {
            out.add(describe(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", out.size());
        result.put("total", page.getTotal());
        result.put("items", out);
        if (out.isEmpty()) {
            result.put("hint", "未匹配到全部关键词（多个词必须同时命中）。可只用其中一个词重试，"
                + "例如只用尺寸“" + keyword.replaceAll("[\\u4e00-\\u9fff]+", "").trim()
                + "”、或只用商品名再搜；系统按商品名/规格/编号/条码包含匹配，俗称可能与登记名不同。");
        } else if (page.getTotal() > out.size()) {
            result.put("hint", "还有更多结果未列出，可加关键词缩小范围或调大 limit");
        }
        return objectMapper.writeValueAsString(result);
    }

    /** 一条商品规格的对模型描述（其它工具也复用这个字段顺序） */
    static Map<String, Object> describe(ItemSkuMapVo row) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (row.getItem() != null) {
            m.put("商品", row.getItem().getItemName());
            m.put("商品编号", row.getItem().getItemCode());
            m.put("单位", row.getItem().getUnit());
        }
        if (row.getItemSku() != null) {
            m.put("规格", row.getItemSku().getSkuName());
            m.put("规格编号", row.getItemSku().getSkuCode());
            m.put("条码", row.getItemSku().getBarcode());
            m.put("售价", row.getItemSku().getSellingPrice());
            m.put("进价", row.getItemSku().getCostPrice());
            m.put("skuId", row.getItemSku().getId());
        }
        return m;
    }
}
