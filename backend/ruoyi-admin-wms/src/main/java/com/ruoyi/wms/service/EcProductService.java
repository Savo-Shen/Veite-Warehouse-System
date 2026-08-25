package com.ruoyi.wms.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.SpringUtils;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.EcCategoryPlatformBo;
import com.ruoyi.wms.domain.bo.EcProductBo;
import com.ruoyi.wms.domain.bo.EcSkuMeasureBo;
import com.ruoyi.wms.domain.bo.EcTitleGenBo;
import com.ruoyi.wms.domain.entity.EcCategory;
import com.ruoyi.wms.domain.entity.EcProduct;
import com.ruoyi.wms.domain.entity.ItemSku;
import com.ruoyi.wms.domain.vo.EcCategoryVo;
import com.ruoyi.wms.domain.vo.EcListingSkuVo;
import com.ruoyi.wms.domain.vo.EcGenTaskVo;
import com.ruoyi.wms.domain.vo.EcProductVo;
import com.ruoyi.wms.mapper.EcCategoryMapper;
import com.ruoyi.wms.mapper.EcProductMapper;
import com.ruoyi.wms.mapper.ItemSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 电商上新Service业务层处理
 *
 * @author savo
 * @date 2026-08-23
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EcProductService {

    private final EcProductMapper ecProductMapper;
    private final EcCategoryMapper ecCategoryMapper;
    private final ItemSkuMapper itemSkuMapper;
    private final EcTitleAiService ecTitleAiService;

    /** 上新列表 */
    public TableDataInfo<EcProductVo> queryPageList(EcProductBo bo, PageQuery pageQuery) {
        IPage<EcProductVo> page = ecProductMapper.selectListingPage(pageQuery.build(), bo);
        return TableDataInfo.build(page);
    }

    /** 单个电商商品的 SKU 明细 */
    public List<EcListingSkuVo> querySkus(Long productId) {
        return ecProductMapper.selectSkusByProductId(productId);
    }

    /** 电商类目（含商品计数），供筛选下拉与平台类目 ID 回填 */
    public List<EcCategoryVo> queryCategories() {
        return ecCategoryMapper.selectWithCount();
    }

    /** 保存标题、卖点、备注 */
    public void updateByBo(EcProductBo bo) {
        EcProduct update = new EcProduct();
        update.setId(bo.getId());
        update.setEcTitle(bo.getEcTitle());
        update.setSellingPoints(bo.getSellingPoints());
        update.setRemark(bo.getRemark());
        if (bo.getAttrs() != null) {
            update.setAttrs(bo.getAttrs());
        }
        ecProductMapper.updateById(update);
    }

    /** 标记上架状态 */
    public void updateStatus(Long id, String status) {
        if (status == null || status.isBlank()) {
            throw new ServiceException("状态不能为空");
        }
        EcProduct update = new EcProduct();
        update.setId(id);
        update.setStatus(status);
        ecProductMapper.updateById(update);
    }

    /**
     * 回填平台类目 ID。只更新三个平台的 ID 与路径，其余字段不可经此接口改动。
     * 用 UpdateWrapper 显式 set，这样清空某个平台 ID 也能真正写回 NULL
     * （全局 updateStrategy 是 NOT_NULL，updateById 会跳过 null 字段）。
     */
    public void updateCategoryPlatformId(EcCategoryPlatformBo bo) {
        LambdaUpdateWrapper<EcCategory> lu = Wrappers.<EcCategory>lambdaUpdate()
            .eq(EcCategory::getId, bo.getId())
            .set(EcCategory::getPddCatId, blankToNull(bo.getPddCatId()))
            .set(EcCategory::getPddCatPath, blankToNull(bo.getPddCatPath()))
            .set(EcCategory::getTbCatId, blankToNull(bo.getTbCatId()))
            .set(EcCategory::getTbCatPath, blankToNull(bo.getTbCatPath()))
            .set(EcCategory::getDyCatId, blankToNull(bo.getDyCatId()))
            .set(EcCategory::getDyCatPath, blankToNull(bo.getDyCatPath()));
        if (ecCategoryMapper.update(null, lu) == 0) {
            throw new ServiceException("类目不存在");
        }
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * 录入实测数据（重量、包装尺寸，顺带改价）。写进 wms_item_sku——这几项是仓库与
     * 电商共用的事实。
     * <p>
     * 必须用 UpdateWrapper 显式 set 而不是 updateById：全局 updateStrategy 是 NOT_NULL，
     * updateById 会跳过 null 字段，导致「把填错的重量清空」永远做不到。称重是这个功能
     * 的核心，改不回去是硬伤。
     * <p>
     * 前端只提交改动过的行，所以这里的 list 通常只有几条，循环更新的代价可以忽略。
     */
    @Transactional
    public int saveMeasures(List<EcSkuMeasureBo> list) {
        if (CollUtil.isEmpty(list)) {
            return 0;
        }
        int n = 0;
        for (EcSkuMeasureBo m : list) {
            LambdaUpdateWrapper<ItemSku> lu = Wrappers.<ItemSku>lambdaUpdate()
                .eq(ItemSku::getId, m.getSkuId())
                .set(ItemSku::getNetWeight, m.getNetWeight())
                .set(ItemSku::getGrossWeight, m.getGrossWeight())
                .set(ItemSku::getPackLength, m.getPackLength())
                .set(ItemSku::getPackWidth, m.getPackWidth())
                .set(ItemSku::getPackHeight, m.getPackHeight())
                .set(ItemSku::getCostPrice, m.getCostPrice())
                .set(ItemSku::getSellingPrice, m.getSellingPrice());
            n += itemSkuMapper.update(null, lu);
        }
        return n;
    }

    /* ---------------- AI 批量生成标题（异步任务 + 进度轮询） ---------------- */

    /**
     * 进行中与最近完成的任务。放内存即可：任务本身只有几分钟，且每生成一个商品的标题
     * 就立刻落库，进程重启后重新提交只会跳过已生成的，不会丢结果。
     */
    private final Map<String, EcGenTaskVo> genTasks = new ConcurrentHashMap<>();

    /** 保留的历史任务数，超出后清理最早的，避免内存无上限增长 */
    private static final int MAX_KEPT_TASKS = 20;

    /**
     * 提交批量生成任务，立即返回 taskId。
     * <p>
     * 实测 AI 单次约 2 秒，148 个商品串行接近 5 分钟，同步返回会撞上 HTTP 超时，
     * 且中断后无从得知进度，因此改为异步 + 轮询。
     */
    public EcGenTaskVo submitGenTitles(EcTitleGenBo bo) {
        List<EcProductVo> products = ecProductMapper.selectListingByIds(bo.getProductIds());
        if (CollUtil.isEmpty(products)) {
            throw new ServiceException("没有找到要生成标题的商品");
        }
        EcGenTaskVo task = new EcGenTaskVo();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setTotal(products.size());
        evictOldTasks();
        genTasks.put(task.getTaskId(), task);

        SpringUtils.getAopProxy(this).runGenTitles(task, products, bo);
        return task;
    }

    /** 查询任务进度 */
    public EcGenTaskVo queryGenTask(String taskId) {
        EcGenTaskVo task = genTasks.get(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在或已过期");
        }
        return task;
    }

    /** 实际执行。逐个调用而非一次性喂全部——单商品提示词已含完整属性，拼一起会超长且串味。 */
    @Async
    public void runGenTitles(EcGenTaskVo task, List<EcProductVo> products, EcTitleGenBo bo) {
        boolean overwrite = Boolean.TRUE.equals(bo.getOverwrite());
        try {
            for (EcProductVo p : products) {
                task.setCurrent(p.getEcName());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", p.getId());
                row.put("ecName", p.getEcName());

                if (!overwrite && p.getEcTitle() != null && !p.getEcTitle().isBlank()) {
                    row.put("state", "skipped");
                    row.put("message", "已有标题，未覆盖");
                    task.setSkipped(task.getSkipped() + 1);
                } else {
                    try {
                        List<EcListingSkuVo> skus = ecProductMapper.selectSkusByProductId(p.getId());
                        EcTitleAiService.GenResult g = ecTitleAiService.generate(p, skus, bo.getExtraHint());

                        EcProduct update = new EcProduct();
                        update.setId(p.getId());
                        update.setEcTitle(g.title());
                        update.setSellingPoints(g.sellingPoints());
                        ecProductMapper.updateById(update);

                        row.put("state", "ok");
                        row.put("title", g.title());
                        row.put("sellingPoints", g.sellingPoints());
                        task.setOk(task.getOk() + 1);
                    } catch (Exception ex) {
                        log.warn("生成标题失败：{}", p.getEcName(), ex);
                        row.put("state", "failed");
                        row.put("message", ex.getMessage());
                        task.setFailed(task.getFailed() + 1);
                    }
                }
                task.getResults().add(row);
                task.setFinished(task.getFinished() + 1);
            }
            task.setState("done");
        } catch (Exception ex) {
            log.error("批量生成标题任务异常", ex);
            task.setState("failed");
            task.setMessage(ex.getMessage());
        } finally {
            task.setCurrent(null);
            task.setEndTime(LocalDateTime.now());
        }
    }

    /** 只保留最近 MAX_KEPT_TASKS 个已结束的任务 */
    private void evictOldTasks() {
        if (genTasks.size() <= MAX_KEPT_TASKS) {
            return;
        }
        genTasks.entrySet().stream()
            .filter(e -> !"running".equals(e.getValue().getState()))
            .sorted((a, b) -> a.getValue().getStartTime().compareTo(b.getValue().getStartTime()))
            .limit(genTasks.size() - MAX_KEPT_TASKS)
            .map(Map.Entry::getKey)
            .toList()
            .forEach(genTasks::remove);
    }

}
