package com.ruoyi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.constant.HttpStatus;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.MapstructUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.MerchantBo;
import com.ruoyi.wms.domain.entity.Merchant;
import com.ruoyi.wms.domain.entity.ReceiptOrder;
import com.ruoyi.wms.domain.vo.MerchantVo;
import com.ruoyi.wms.mapper.MerchantMapper;
import com.ruoyi.wms.mapper.ReceiptOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
// import java.util.Map;

/**
 * 往来单位Service业务层处理
 *
 * @author zcc
 * @date 2024-07-16
 */
@RequiredArgsConstructor
@Service
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final ReceiptOrderMapper receiptOrderMapper;

    /**
     * 各企业类型的编号起始段：客户 2xx、供应商 0xx、物流单位 3xx
     */
    private static final Map<Integer, Integer> CODE_BASE = Map.of(
        1, 200,
        2, 0,
        3, 300
    );

    /**
     * 查询往来单位
     */
    public MerchantVo queryById(Long id){
        return merchantMapper.selectVoById(id);
    }

    /**
     * 查询往来单位列表
     */
    public TableDataInfo<MerchantVo> queryPageList(MerchantBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<Merchant> lqw = buildQueryWrapper(bo);
        Page<MerchantVo> result = merchantMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询往来单位列表
     */
    public List<MerchantVo> queryList(MerchantBo bo) {
        LambdaQueryWrapper<Merchant> lqw = buildQueryWrapper(bo);
        return merchantMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<Merchant> buildQueryWrapper(MerchantBo bo) {
        // Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<Merchant> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getMerchantCode()), Merchant::getMerchantCode, bo.getMerchantCode());
        lqw.like(StringUtils.isNotBlank(bo.getMerchantName()), Merchant::getMerchantName, bo.getMerchantName());
        lqw.eq(bo.getMerchantType() != null, Merchant::getMerchantType, bo.getMerchantType());
        return lqw;
    }

    /**
     * 生成下一个可用编号：同类型中最大编号 + 1，并跳过已被占用的编号
     */
    public String generateMerchantCode(Integer merchantType) {
        List<Merchant> all = merchantMapper.selectList(Wrappers.<Merchant>lambdaQuery()
            .select(Merchant::getMerchantCode, Merchant::getMerchantType));
        Set<String> usedCodes = new HashSet<>();
        int max = merchantType == null ? 0 : CODE_BASE.getOrDefault(merchantType, 0);
        for (Merchant merchant : all) {
            String code = merchant.getMerchantCode();
            if (StringUtils.isBlank(code)) {
                continue;
            }
            usedCodes.add(code.trim());
            // 只按同类型的编号递增，其它类型的编号仅用于避让重复
            if (!Objects.equals(merchant.getMerchantType(), merchantType)) {
                continue;
            }
            Integer value = parseCode(code);
            if (value != null && value > max) {
                max = value;
            }
        }
        int next = max + 1;
        while (usedCodes.contains(formatCode(next))) {
            next++;
        }
        return formatCode(next);
    }

    private Integer parseCode(String code) {
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{1,9}")) {
            return null;
        }
        return Integer.parseInt(trimmed);
    }

    private String formatCode(int value) {
        return String.format("%03d", value);
    }

    /**
     * 新增往来单位
     */
    public void insertByBo(MerchantBo bo) {
        Merchant add = MapstructUtils.convert(bo, Merchant.class);
        if (StringUtils.isBlank(add.getMerchantCode())) {
            add.setMerchantCode(generateMerchantCode(add.getMerchantType()));
        }
        merchantMapper.insert(add);
    }

    /**
     * 修改往来单位
     */
    public void updateByBo(MerchantBo bo) {
        Merchant update = MapstructUtils.convert(bo, Merchant.class);
        merchantMapper.updateById(update);
    }

    /**
     * 删除往来单位
     */
    public void deleteById(Long id) {
        validateIdBeforeDelete(id);
        merchantMapper.deleteById(id);
    }

    private void validateIdBeforeDelete(Long id) {
        LambdaQueryWrapper<ReceiptOrder> receiptOrderLqw = Wrappers.lambdaQuery();
        receiptOrderLqw.eq(ReceiptOrder::getMerchantId, id);
        Long receiptOrderCount = receiptOrderMapper.selectCount(receiptOrderLqw);
        if (receiptOrderCount != null && receiptOrderCount > 0) {
            throw new ServiceException("删除失败", HttpStatus.CONFLICT,"该企业已有业务关联，无法删除！");
        }
    }

    /**
     * 批量删除往来单位
     */
    public void deleteByIds(Collection<Long> ids) {
        merchantMapper.deleteBatchIds(ids);
    }
}
