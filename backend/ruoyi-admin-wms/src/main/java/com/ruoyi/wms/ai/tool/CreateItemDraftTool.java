package com.ruoyi.wms.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.domain.bo.ItemBrandBo;
import com.ruoyi.wms.domain.bo.ItemCategoryBo;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.entity.Item;
import com.ruoyi.wms.domain.entity.Location;
import com.ruoyi.wms.domain.vo.ItemBrandVo;
import com.ruoyi.wms.domain.vo.ItemCategoryVo;
import com.ruoyi.wms.domain.vo.ItemSkuVo;
import com.ruoyi.wms.domain.vo.LocationVo;
import com.ruoyi.wms.mapper.ItemMapper;
import com.ruoyi.wms.mapper.LocationMapper;
import com.ruoyi.wms.service.ItemBrandService;
import com.ruoyi.wms.service.ItemCategoryService;
import com.ruoyi.wms.service.ItemSkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ruoyi.wms.ai.tool.ToolSupport.*;

/**
 * 工具：新建商品（含规格）的建档草稿；商品已存在时转成“给已有商品加规格”。动作草稿，用户点“确认执行”才写入。
 *
 * @author Savo
 */
@Component
@RequiredArgsConstructor
public class CreateItemDraftTool implements AiTool {

    private final ItemMapper itemMapper;
    private final ItemSkuService itemSkuService;
    private final ItemCategoryService itemCategoryService;
    private final ItemBrandService itemBrandService;
    private final LocationMapper locationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String requiredPermission() {
        return "wms:item:edit";
    }

    @Override
    public String name() {
        return "create_item_draft";
    }

    @Override
    public String title() {
        return "正在整理商品建档";
    }

    @Override
    public boolean producesDraft() {
        return true;
    }

    @Override
    public String description() {
        return "新建商品及其规格的建档草稿，生成待确认操作，用户在对话里点“确认执行”后才写入。"
            + "当用户说“新建/加一个商品 X，规格 Y，售价 a 进价 b”，或要把一批新品建档时调用。"
            + "商品名已存在时会自动转成“给已有商品补规格”。分类必须是系统里已有的分类名（如 气缸、气管、阀类、管路连接类）；"
            + "品牌不存在时留空。先用 search_sku 确认没有同名规格，避免重复建档。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> sku = object(props(
            "skuName", string("规格名称/型号，如 SDA20*50、10*6.5/100M透明"),
            "barcode", string("条码（可选）"),
            "costPrice", number("进价（可选）"),
            "sellingPrice", number("售价（可选）"),
            "location", string("库位编码（可选），如 1-D3-2")
        ), List.of("skuName"));
        return object(props(
            "itemName", string("商品名称（品类名，不含型号），如 SDA薄型气缸"),
            "category", string("分类名称，必须是系统已有分类"),
            "brand", string("品牌名（可选）"),
            "unit", string("计量单位（可选），如 个、卷、米、箱"),
            "skus", array("规格列表，至少一条", sku),
            "remark", string("备注（可选）")
        ), List.of("itemName", "category", "skus"));
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String itemName = text(args, "itemName");
        String category = text(args, "category");
        if (itemName == null || category == null) {
            return error("itemName 和 category 不能为空");
        }
        List<String> warnings = new ArrayList<>();

        // 分类：精确名优先，再模糊
        ItemCategoryBo cbo = new ItemCategoryBo();
        cbo.setCategoryName(category);
        List<ItemCategoryVo> cats = itemCategoryService.queryList(cbo);
        ItemCategoryVo cat = cats.stream().filter(c -> category.equals(c.getCategoryName())).findFirst()
            .orElse(cats.isEmpty() ? null : cats.get(0));
        if (cat == null) {
            ItemCategoryBo top = new ItemCategoryBo();
            top.setParentId(0L);
            String names = itemCategoryService.queryList(top).stream().map(ItemCategoryVo::getCategoryName)
                .collect(Collectors.joining("、"));
            return error("没有叫“" + category + "”的分类。现有一级分类：" + names + "。请从中选一个（或让用户先去基础资料里新建分类）。");
        }
        if (cats.size() > 1 && !category.equals(cat.getCategoryName())) {
            warnings.add("分类“" + category + "”匹配到多个，已选“" + cat.getCategoryName() + "”。");
        }

        // 品牌
        Long brandId = null;
        String brand = text(args, "brand");
        if (brand != null) {
            ItemBrandBo bbo = new ItemBrandBo();
            bbo.setBrandName(brand);
            List<ItemBrandVo> brands = itemBrandService.queryList(bbo);
            final String wanted = brand;
            ItemBrandVo b = brands.stream().filter(x -> wanted.equalsIgnoreCase(x.getBrandName())).findFirst()
                .orElse(brands.isEmpty() ? null : brands.get(0));
            if (b == null) {
                warnings.add("品牌“" + brand + "”不存在，先留空；需要的话去基础资料里新建品牌后再改。");
            } else {
                brandId = b.getId();
                brand = b.getBrandName();
            }
        }

        // 商品是否已存在 → 转成补规格
        Item existing = itemMapper.selectOne(Wrappers.<Item>lambdaQuery().eq(Item::getItemName, itemName).last("limit 1"));
        List<String> existingSkuNames = new ArrayList<>();
        if (existing != null) {
            ItemSkuBo q = new ItemSkuBo();
            q.setItemId(existing.getId());
            existingSkuNames = itemSkuService.queryList(q).stream().map(ItemSkuVo::getSkuName).toList();
        }

        List<Map<String, Object>> skus = new ArrayList<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        JsonNode skuNode = args.path("skus");
        if (skuNode.isArray()) {
            for (JsonNode s : skuNode) {
                String skuName = text(s, "skuName");
                if (skuName == null) {
                    continue;
                }
                if (existingSkuNames.contains(skuName)) {
                    warnings.add("规格“" + skuName + "”在商品“" + itemName + "”下已存在，已跳过。");
                    continue;
                }
                String locationId = null;
                String locCode = text(s, "location");
                if (locCode != null) {
                    List<LocationVo> locs = locationMapper.selectVoList(Wrappers.<Location>lambdaQuery()
                        .eq(Location::getLocationCode, locCode).last("limit 1"));
                    if (locs.isEmpty()) {
                        warnings.add("库位“" + locCode + "”不存在，规格“" + skuName + "”的库位先留空。");
                    } else {
                        locationId = String.valueOf(locs.get(0).getId());
                        locCode = locs.get(0).getLocationCode();
                    }
                }
                Map<String, Object> sku = new LinkedHashMap<>();
                sku.put("skuName", skuName);
                sku.put("barcode", text(s, "barcode"));
                sku.put("costPrice", decimal(s, "costPrice"));
                sku.put("sellingPrice", decimal(s, "sellingPrice"));
                sku.put("itemLocationId", locationId);
                skus.add(sku);

                Map<String, Object> line = new LinkedHashMap<>();
                line.put("规格", skuName);
                line.put("条码", sku.get("barcode"));
                line.put("进价", sku.get("costPrice"));
                line.put("售价", sku.get("sellingPrice"));
                line.put("库位", locationId == null ? null : locCode);
                lines.add(line);
            }
        }
        if (skus.isEmpty()) {
            return error("没有可建的规格（skus 为空或全部已存在）");
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemName", itemName);
        item.put("itemCategory", String.valueOf(cat.getId()));
        item.put("categoryName", cat.getCategoryName());
        item.put("unit", text(args, "unit"));
        item.put("itemBrand", brandId);
        item.put("brandName", brandId == null ? null : brand);
        item.put("remark", text(args, "remark"));
        if (existing != null) {
            item.put("itemId", existing.getId());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("item", item);
        payload.put("skus", skus);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "action");
        draft.put("action", existing == null ? "create_item" : "add_skus");
        draft.put("title", existing == null
            ? "新建商品：" + itemName + "（" + cat.getCategoryName() + "，" + skus.size() + " 个规格）"
            : "给已有商品“" + itemName + "”补 " + skus.size() + " 个规格");
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("商品", itemName);
        head.put("分类", cat.getCategoryName());
        head.put("品牌", brandId == null ? null : brand);
        head.put("单位", item.get("unit"));
        draft.put("summary", head);
        draft.put("lines", lines);
        draft.put("payload", payload);
        draft.put("warnings", warnings);
        draft.put("executed", false);
        draft.put("说明", "待确认操作：用户在对话里点“确认执行”后才会写入。");
        return objectMapper.writeValueAsString(draft);
    }

    private String error(String msg) throws Exception {
        return objectMapper.writeValueAsString(Map.of("error", msg));
    }
}
