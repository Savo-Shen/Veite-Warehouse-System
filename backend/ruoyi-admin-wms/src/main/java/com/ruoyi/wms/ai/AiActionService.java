package com.ruoyi.wms.ai;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.satoken.utils.LoginHelper;
import com.ruoyi.wms.ai.domain.AiMessage;
import com.ruoyi.wms.domain.bo.ItemBo;
import com.ruoyi.wms.domain.bo.ItemSkuBo;
import com.ruoyi.wms.domain.bo.MerchantBo;
import com.ruoyi.wms.mapper.ItemSkuMapper;
import com.ruoyi.wms.service.ItemService;
import com.ruoyi.wms.service.ItemSkuService;
import com.ruoyi.wms.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行 AI 生成的“动作草稿”（type=action）：库位调整、新建往来单位、新建商品/补规格。
 * <p>
 * 草稿存在消息的 draft 字段里；用户在对话里点“确认执行”才走到这里。
 * 执行完把 executed 标记回写进草稿，防止刷新页面后再点一次重复建档。
 * 权限在这里按动作再查一次——草稿是模型生成的，不能当可信输入。
 *
 * @author Savo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiActionService {

    private final AiConversationService conversationService;
    private final MerchantService merchantService;
    private final ItemService itemService;
    private final ItemSkuService itemSkuService;
    private final ItemSkuMapper itemSkuMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> execute(Long messageId) {
        AiMessage msg = conversationService.getMessage(messageId);
        if (msg == null || msg.getDraft() == null || msg.getDraft().isBlank()) {
            throw new ServiceException("这条消息没有可执行的操作");
        }
        conversationService.requireOwned(msg.getConversationId(), LoginHelper.getUserId());

        Map<String, Object> draft;
        try {
            draft = objectMapper.readValue(msg.getDraft(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new ServiceException("草稿内容无法解析");
        }
        if (!"action".equals(draft.get("type"))) {
            throw new ServiceException("这条消息不是待确认操作");
        }
        if (Boolean.TRUE.equals(draft.get("executed"))) {
            throw new ServiceException("这个操作已经执行过了（" + draft.get("result") + "），不能重复执行");
        }
        String action = String.valueOf(draft.get("action"));
        Map<String, Object> payload = asMap(draft.get("payload"));
        if (payload == null) {
            throw new ServiceException("草稿缺少 payload");
        }

        String result = switch (action) {
            case "relocate_sku" -> relocate(payload);
            case "create_merchant" -> createMerchant(payload);
            case "create_item" -> createItem(payload);
            case "add_skus" -> addSkus(payload);
            default -> throw new ServiceException("未知操作: " + action);
        };

        draft.put("executed", true);
        draft.put("executedAt", LocalDateTime.now().toString());
        draft.put("result", result);
        try {
            conversationService.updateDraft(messageId, objectMapper.writeValueAsString(draft));
        } catch (Exception e) {
            throw new ServiceException("回写执行状态失败: " + e.getMessage());
        }
        log.info("AI 动作已执行: message={}, action={}, result={}", messageId, action, result);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", result);
        out.put("draft", draft);
        return out;
    }

    private String relocate(Map<String, Object> payload) {
        StpUtil.checkPermission("wms:item:edit");
        String locationId = String.valueOf(payload.get("locationId"));
        List<Long> skuIds = asLongList(payload.get("skuIds"));
        if (skuIds.isEmpty()) {
            throw new ServiceException("没有要调整的商品");
        }
        for (Long skuId : skuIds) {
            itemSkuMapper.updateItemLocationIdById(skuId, locationId);
        }
        String code = str(payload.get("locationCode"));
        return "已把 " + skuIds.size() + " 个规格的库位改为 " + (code == null ? locationId : code);
    }

    private String createMerchant(Map<String, Object> payload) {
        StpUtil.checkPermission("wms:merchant:edit");
        MerchantBo bo = new MerchantBo();
        bo.setMerchantName(str(payload.get("merchantName")));
        Integer type = payload.get("merchantType") == null ? null : Integer.valueOf(String.valueOf(payload.get("merchantType")));
        if (bo.getMerchantName() == null || type == null) {
            throw new ServiceException("名称或类型缺失");
        }
        bo.setMerchantType(type);
        bo.setContactPerson(str(payload.get("contactPerson")));
        bo.setMobile(str(payload.get("mobile")));
        bo.setTel(str(payload.get("tel")));
        bo.setAddress(str(payload.get("address")));
        bo.setRemark(str(payload.get("remark")));
        bo.setMerchantCode(merchantService.generateMerchantCode(type));
        merchantService.insertByBo(bo);
        String typeText = switch (type) {
            case 1 -> "客户";
            case 2 -> "供应商";
            case 3 -> "物流单位";
            default -> "往来单位";
        };
        return "已新建" + typeText + "「" + bo.getMerchantName() + "」，编号 " + bo.getMerchantCode();
    }

    private String createItem(Map<String, Object> payload) {
        StpUtil.checkPermission("wms:item:edit");
        Map<String, Object> item = asMap(payload.get("item"));
        if (item == null || str(item.get("itemName")) == null) {
            throw new ServiceException("商品信息缺失");
        }
        ItemBo bo = new ItemBo();
        bo.setItemName(str(item.get("itemName")));
        bo.setItemCategory(str(item.get("itemCategory")));
        bo.setUnit(str(item.get("unit")));
        bo.setItemBrand(item.get("itemBrand") == null ? null : Long.valueOf(String.valueOf(item.get("itemBrand"))));
        bo.setRemark(str(item.get("remark")));
        bo.setTagIds(new ArrayList<>());
        List<ItemSkuBo> skus = toSkuBos(payload.get("skus"), null);
        if (skus.isEmpty()) {
            throw new ServiceException("至少要有一个规格");
        }
        bo.setSku(skus);
        itemService.insertByForm(bo);
        return "已新建商品「" + bo.getItemName() + "」，" + skus.size() + " 个规格";
    }

    private String addSkus(Map<String, Object> payload) {
        StpUtil.checkPermission("wms:item:edit");
        Map<String, Object> item = asMap(payload.get("item"));
        if (item == null || item.get("itemId") == null) {
            throw new ServiceException("缺少要补规格的商品 id");
        }
        Long itemId = Long.valueOf(String.valueOf(item.get("itemId")));
        List<ItemSkuBo> skus = toSkuBos(payload.get("skus"), itemId);
        if (skus.isEmpty()) {
            throw new ServiceException("没有要补的规格");
        }
        for (ItemSkuBo sku : skus) {
            itemSkuService.insertByBo(sku);
        }
        return "已给商品「" + str(item.get("itemName")) + "」补 " + skus.size() + " 个规格";
    }

    private List<ItemSkuBo> toSkuBos(Object raw, Long itemId) {
        List<ItemSkuBo> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            Map<String, Object> m = asMap(o);
            if (m == null || str(m.get("skuName")) == null) {
                continue;
            }
            ItemSkuBo b = new ItemSkuBo();
            b.setItemId(itemId);
            b.setSkuName(str(m.get("skuName")));
            b.setBarcode(str(m.get("barcode")));
            b.setCostPrice(dec(m.get("costPrice")));
            b.setSellingPrice(dec(m.get("sellingPrice")));
            b.setItemLocationId(str(m.get("itemLocationId")));
            out.add(b);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static List<Long> asLongList(Object o) {
        List<Long> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x != null) {
                    out.add(Long.valueOf(String.valueOf(x)));
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
