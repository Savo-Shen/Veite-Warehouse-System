package com.ruoyi.wms.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.MapstructUtils;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.ItemTagAssignBo;
import com.ruoyi.wms.domain.bo.ItemTagBo;
import com.ruoyi.wms.domain.entity.ItemTag;
import com.ruoyi.wms.domain.entity.ItemTagRel;
import com.ruoyi.wms.domain.vo.ItemTagVo;
import com.ruoyi.wms.mapper.ItemTagMapper;
import com.ruoyi.wms.mapper.ItemTagRelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品标签Service业务层处理
 *
 * @author savo
 * @date 2026-06-20
 */
@RequiredArgsConstructor
@Service
public class ItemTagService {

    private final ItemTagMapper itemTagMapper;
    private final ItemTagRelMapper itemTagRelMapper;

    /**
     * 查询商品标签
     */
    public ItemTagVo queryById(Long id) {
        return itemTagMapper.selectVoById(id);
    }

    /**
     * 分页查询商品标签
     */
    public TableDataInfo<ItemTagVo> queryPageList(ItemTagBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ItemTag> lqw = buildQueryWrapper(bo);
        Page<ItemTagVo> result = itemTagMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询商品标签列表（不分页）
     */
    public List<ItemTagVo> queryList(ItemTagBo bo) {
        return itemTagMapper.selectVoList(buildQueryWrapper(bo));
    }

    private LambdaQueryWrapper<ItemTag> buildQueryWrapper(ItemTagBo bo) {
        LambdaQueryWrapper<ItemTag> lqw = Wrappers.lambdaQuery();
        lqw.like(StrUtil.isNotBlank(bo.getTagName()), ItemTag::getTagName, bo.getTagName());
        lqw.orderByDesc(ItemTag::getCreateTime);
        return lqw;
    }

    /**
     * 新增商品标签
     */
    public void insertByBo(ItemTagBo bo) {
        validateTagNameUnique(bo);
        ItemTag add = MapstructUtils.convert(bo, ItemTag.class);
        itemTagMapper.insert(add);
    }

    /**
     * 修改商品标签
     */
    public void updateByBo(ItemTagBo bo) {
        validateTagNameUnique(bo);
        ItemTag update = MapstructUtils.convert(bo, ItemTag.class);
        itemTagMapper.updateById(update);
    }

    private void validateTagNameUnique(ItemTagBo bo) {
        LambdaQueryWrapper<ItemTag> lqw = Wrappers.lambdaQuery();
        lqw.eq(ItemTag::getTagName, bo.getTagName());
        lqw.ne(bo.getId() != null, ItemTag::getId, bo.getId());
        if (itemTagMapper.exists(lqw)) {
            throw new ServiceException("标签名称已存在");
        }
    }

    /**
     * 删除商品标签（同时删除关联关系）
     */
    @Transactional
    public void deleteById(Long id) {
        itemTagMapper.deleteById(id);
        itemTagRelMapper.delete(Wrappers.<ItemTagRel>lambdaQuery().eq(ItemTagRel::getTagId, id));
    }

    /**
     * 批量给商品打标签（追加，已存在的组合自动跳过）
     */
    @Transactional
    public void assignTags(ItemTagAssignBo bo) {
        List<Long> itemIds = bo.getItemIds();
        List<Long> tagIds = bo.getTagIds();
        // 已存在的关联，避免重复
        List<ItemTagRel> existList = itemTagRelMapper.selectList(
            Wrappers.<ItemTagRel>lambdaQuery()
                .in(ItemTagRel::getItemId, itemIds)
                .in(ItemTagRel::getTagId, tagIds));
        Set<String> existKeys = existList.stream()
            .map(rel -> rel.getItemId() + ":" + rel.getTagId())
            .collect(Collectors.toSet());

        List<ItemTagRel> insertList = new LinkedList<>();
        for (Long itemId : itemIds) {
            for (Long tagId : tagIds) {
                if (existKeys.contains(itemId + ":" + tagId)) {
                    continue;
                }
                ItemTagRel rel = new ItemTagRel();
                rel.setItemId(itemId);
                rel.setTagId(tagId);
                insertList.add(rel);
            }
        }
        if (CollUtil.isNotEmpty(insertList)) {
            itemTagRelMapper.insertBatch(insertList);
        }
    }

    /**
     * 设置某个商品的标签（替换语义：传入的即为该商品最终的标签集合）。
     * tagIds 为 null 时不处理；为空集合时清空该商品的全部标签。
     */
    @Transactional
    public void setItemTags(Long itemId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        // 先清除原有关联
        itemTagRelMapper.delete(Wrappers.<ItemTagRel>lambdaQuery().eq(ItemTagRel::getItemId, itemId));
        if (CollUtil.isEmpty(tagIds)) {
            return;
        }
        List<ItemTagRel> insertList = tagIds.stream().distinct().map(tagId -> {
            ItemTagRel rel = new ItemTagRel();
            rel.setItemId(itemId);
            rel.setTagId(tagId);
            return rel;
        }).collect(Collectors.toList());
        itemTagRelMapper.insertBatch(insertList);
    }

    /**
     * 移除某个商品的某个标签
     */
    public void removeRel(Long itemId, Long tagId) {
        itemTagRelMapper.delete(
            Wrappers.<ItemTagRel>lambdaQuery()
                .eq(ItemTagRel::getItemId, itemId)
                .eq(ItemTagRel::getTagId, tagId));
    }

    /**
     * 根据商品ID集合查询每个商品的标签列表，返回 itemId -> tags 的映射
     */
    public Map<Long, List<ItemTagVo>> queryTagMapByItemIds(Collection<Long> itemIds) {
        if (CollUtil.isEmpty(itemIds)) {
            return Map.of();
        }
        List<ItemTagRel> relList = itemTagRelMapper.selectList(
            Wrappers.<ItemTagRel>lambdaQuery().in(ItemTagRel::getItemId, itemIds));
        if (CollUtil.isEmpty(relList)) {
            return Map.of();
        }
        Set<Long> tagIds = relList.stream().map(ItemTagRel::getTagId).collect(Collectors.toSet());
        Map<Long, ItemTagVo> tagVoMap = itemTagMapper.selectVoBatchIds(tagIds).stream()
            .collect(Collectors.toMap(ItemTagVo::getId, t -> t, (a, b) -> a));

        Map<Long, List<ItemTagVo>> result = new HashMap<>();
        for (ItemTagRel rel : relList) {
            ItemTagVo tag = tagVoMap.get(rel.getTagId());
            if (tag != null) {
                result.computeIfAbsent(rel.getItemId(), k -> new LinkedList<>()).add(tag);
            }
        }
        return result;
    }

}
