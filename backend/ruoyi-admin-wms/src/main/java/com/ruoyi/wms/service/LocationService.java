package com.ruoyi.wms.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.utils.MapstructUtils;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.LocationBo;
import com.ruoyi.wms.domain.entity.Location;
import com.ruoyi.wms.domain.vo.LocationVo;
import com.ruoyi.wms.mapper.LocationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
@Log4j2
public class LocationService {

    private final LocationMapper locationMapper;

    /**
     * 根据ID查询
     */
    public LocationVo queryById(Long id) {
        return locationMapper.selectVoById(id);
    }

    /**
     * 批量查询
     */
    public List<LocationVo> queryByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return CollUtil.newArrayList();
        }
        LambdaQueryWrapper<Location> lqw = Wrappers.lambdaQuery();
        lqw.in(Location::getId, ids);
        return locationMapper.selectVoList(lqw);
    }

    /**
     * 分页查询
     */
    public TableDataInfo<LocationVo> queryPageList(LocationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Location> lqw = buildQueryWrapper(bo);
        Page<LocationVo> page = locationMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 列表查询
     */
    public List<LocationVo> queryList(LocationBo bo) {
        LambdaQueryWrapper<Location> lqw = buildQueryWrapper(bo);
        lqw.orderByAsc(Location::getLocationCode);
        return locationMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<Location> buildQueryWrapper(LocationBo bo) {
        LambdaQueryWrapper<Location> lqw = Wrappers.lambdaQuery();
        lqw.eq(StrUtil.isNotBlank(bo.getLocationCode()), Location::getLocationCode, bo.getLocationCode());
        lqw.like(StrUtil.isNotBlank(bo.getLocationName()), Location::getLocationName, bo.getLocationName());
        lqw.eq(bo.getWarehouseId() != null, Location::getWarehouseId, bo.getWarehouseId());
        return lqw;
    }

    /**
     * 新增
     */
    @Transactional
    public void insertByForm(LocationBo bo) {
        validateBeforeSave(bo);
        Location location = MapstructUtils.convert(bo, Location.class);
        locationMapper.insert(location);
    }

    /**
     * 修改
     */
    @Transactional
    public void updateByForm(LocationBo bo) {
        validateBeforeSave(bo);
        Location location = MapstructUtils.convert(bo, Location.class);
        locationMapper.updateById(location);
    }

    /**
     * 删除
     */
    @Transactional
    public void deleteById(Long id) {
        locationMapper.deleteById(id);
    }

    /**
     * 保存前校验
     */
    private void validateBeforeSave(LocationBo bo) {
        // 校验名称唯一
        LambdaQueryWrapper<Location> lqw = Wrappers.lambdaQuery();
        lqw.eq(Location::getLocationName, bo.getLocationName());
        lqw.ne(bo.getId() != null, Location::getId, bo.getId());
        Assert.isTrue(locationMapper.selectCount(lqw) == 0, "位置名称重复");
    }
}