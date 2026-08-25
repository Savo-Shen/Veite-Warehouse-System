package com.ruoyi.wms.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.mybatis.core.page.PageQuery;
import com.ruoyi.common.mybatis.core.page.TableDataInfo;
import com.ruoyi.wms.domain.bo.HoseFittingBo;
import com.ruoyi.wms.domain.bo.HoseQuoteBo;
import com.ruoyi.wms.domain.entity.HoseCrimp;
import com.ruoyi.wms.domain.entity.HosePiece;
import com.ruoyi.wms.domain.vo.*;
import com.ruoyi.wms.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 压油管 Service
 * <p>
 * 这个模块解决的是柜台上最常见的一句话：「接一根四分两层、1米2、两头22×1.5的A型面，
 * 有没有料、多少钱、能不能现在压」。所以核心就一个 {@link #quote} 方法，
 * 把胶管、两端接头、两个外套一次算完，给位置、给教程、给报价。
 *
 * @author savo
 * @date 2026-08-23
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class HoseService {

    private final HoseSpecMapper hoseSpecMapper;
    private final HosePieceMapper hosePieceMapper;
    private final HoseFittingMapper hoseFittingMapper;
    private final HoseFerruleMapper hoseFerruleMapper;
    private final HoseCrimpMapper hoseCrimpMapper;

    /** 售价 = 进价 × 2（2026-08-23 用户定的）。要改加价率就改这一个常量 */
    private static final BigDecimal SELL_MARKUP = new BigDecimal("2");

    private static final String DEFAULT_SKIN = "非剥皮";

    /** 状态字面量。qty 为 null 是「还没盘」，跟「盘过没有」必须分开 */
    private static final String ST_OK = "够";
    private static final String ST_SHORT = "缺";
    private static final String ST_UNCOUNTED = "未盘";
    private static final String ST_NO_RECORD = "无档案";

    // ============================================================
    // 列表查询
    // ============================================================

    /** 胶管规格 + 在库汇总 */
    public List<HoseSpecVo> querySpecList(String keyword, Boolean onlyInStock) {
        return hoseSpecMapper.selectWithStock(keyword, onlyInStock);
    }

    /** 胶管在库分段明细 */
    public List<HosePieceVo> queryPieceList(String keyword) {
        return hosePieceMapper.selectAllInStock(keyword);
    }

    /** 接头列表 */
    public TableDataInfo<HoseFittingVo> queryFittingPage(HoseFittingBo bo, PageQuery pageQuery) {
        IPage<HoseFittingVo> page = hoseFittingMapper.selectFittingPage(pageQuery.build(), bo);
        return TableDataInfo.build(page);
    }

    /** 接头下拉选项：配料时选两端接头用，只取前 60 条 */
    public List<HoseFittingVo> queryFittingOptions(String keyword) {
        HoseFittingBo bo = new HoseFittingBo();
        bo.setKeyword(keyword);
        PageQuery pq = new PageQuery();
        pq.setPageNum(1);
        pq.setPageSize(60);
        return hoseFittingMapper.selectFittingPage(pq.build(), bo).getRecords();
    }

    /** 扣压外套列表 */
    public List<HoseFerruleVo> queryFerruleList(String keyword) {
        return hoseFerruleMapper.selectFerruleList(keyword);
    }

    /** 扣压参数列表 */
    public List<HoseCrimpVo> queryCrimpList() {
        return hoseCrimpMapper.selectCrimpList();
    }

    // ============================================================
    // 配料查询 —— 本模块的主功能
    // ============================================================

    public HoseQuoteVo quote(HoseQuoteBo bo) {
        HoseSpecVo spec = hoseSpecMapper.selectOneWithStock(bo.getHoseCode());
        if (spec == null) {
            throw new ServiceException("没有这个胶管规格：" + bo.getHoseCode());
        }
        BigDecimal need = bo.getLengthM();
        if (need == null || need.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("长度要大于 0");
        }
        int asmQty = bo.getAssemblyQty() == null || bo.getAssemblyQty() < 1 ? 1 : bo.getAssemblyQty();
        String skin = StrUtil.blankToDefault(bo.getSkinType(), DEFAULT_SKIN);
        String layerScope = layerScopeOf(spec.getLayerCode());

        HoseQuoteVo vo = new HoseQuoteVo();
        vo.setAssemblyQty(asmQty);

        List<BigDecimal> amounts = new ArrayList<>();
        int missingCost = 0;

        // ---------- 胶管 ----------
        List<HosePieceVo> pieces = hosePieceMapper.selectInStock(spec.getHoseCode());
        vo.setAllPieces(pieces);
        vo.setUsablePieces(pieces.stream()
            .filter(p -> p.getLengthM() != null && p.getLengthM().compareTo(need) >= 0)
            .collect(Collectors.toList()));

        // 一段能裁几根：floor(段长 / 需求长)。余料接不上，所以是逐段取整再相加，
        // 不能拿总米数除需求长——那样会把 4 米的零头也算进去。
        int cuttable = pieces.stream()
            .mapToInt(p -> p.getLengthM() == null ? 0
                : p.getLengthM().divideToIntegralValue(need).intValue())
            .sum();

        HoseQuoteLineVo hoseLine = new HoseQuoteLineVo();
        hoseLine.setRole("胶管");
        hoseLine.setCode(spec.getHoseCode());
        hoseLine.setName(String.format("%s %s %s", spec.getNickname(), spec.getInch(), spec.getLayerName()));
        hoseLine.setSpec(String.format("内径 %s mm", trim(spec.getIdMm())));
        hoseLine.setNeedText(need.stripTrailingZeros().toPlainString() + " 米 × " + asmQty + " 根");
        hoseLine.setLocationText(StrUtil.blankToDefault(spec.getLocationNames(), "—"));
        if (pieces.isEmpty()) {
            hoseLine.setStockText("无在库段");
            hoseLine.setStatus(ST_SHORT);
        } else {
            hoseLine.setStockText(String.format("最长一段 %s 米（共 %d 段 %s 米：%s）",
                trim(spec.getMaxLengthM()), spec.getPieceCount(),
                trim(spec.getTotalLengthM()), spec.getPieceText()));
            hoseLine.setStatus(cuttable >= asmQty ? ST_OK : ST_SHORT);
        }
        hoseLine.setUnitCost(spec.getCostPrice());
        if (spec.getCostPrice() != null) {
            BigDecimal amt = spec.getCostPrice().multiply(need)
                .multiply(BigDecimal.valueOf(asmQty)).setScale(2, RoundingMode.HALF_UP);
            hoseLine.setAmount(amt);
            amounts.add(amt);
        } else {
            missingCost++;
        }
        vo.getLines().add(hoseLine);

        if (ST_SHORT.equals(hoseLine.getStatus())) {
            if (pieces.isEmpty()) {
                vo.getBlockers().add(String.format("胶管 %s 一段都没有", spec.getHoseCode()));
            } else if (spec.getMaxLengthM() != null && spec.getMaxLengthM().compareTo(need) < 0) {
                vo.getBlockers().add(String.format(
                    "胶管 %s 合计 %s 米，但最长一段只有 %s 米，接不了 %s 米的（余料不能接）",
                    spec.getHoseCode(), trim(spec.getTotalLengthM()),
                    trim(spec.getMaxLengthM()), trim(need)));
            } else {
                vo.getBlockers().add(String.format("胶管 %s 只够裁 %d 根，要 %d 根",
                    spec.getHoseCode(), cuttable, asmQty));
            }
        }
        if ("推算".equals(spec.getPriceSource())) {
            vo.getWarnings().add("胶管进价是按实价拟合推算的，误差按 ±15% 看，正式报价前问一下供货商");
        }

        // ---------- 两端接头 ----------
        String skuA = StrUtil.trimToNull(bo.getEndASku());
        String skuB = StrUtil.blankToDefault(StrUtil.trimToNull(bo.getEndBSku()), skuA);
        Map<String, HoseFittingVo> fittingMap = new LinkedHashMap<>();
        List<String> wanted = new ArrayList<>();
        if (skuA != null) {
            wanted.add(skuA);
        }
        if (skuB != null && !skuB.equals(skuA)) {
            wanted.add(skuB);
        }
        if (CollUtil.isNotEmpty(wanted)) {
            hoseFittingMapper.selectBySkus(wanted)
                .forEach(f -> fittingMap.put(f.getFittingSku(), f));
        }
        // 两端同款时总需求是 2×根数，要合在一起判库存，否则各判各的会漏判
        Map<String, Integer> fittingNeed = new LinkedHashMap<>();
        if (skuA != null) {
            fittingNeed.merge(skuA, asmQty, Integer::sum);
        }
        if (skuB != null) {
            fittingNeed.merge(skuB, asmQty, Integer::sum);
        }

        missingCost += appendFittingLine(vo, amounts, "A端接头", skuA,
            fittingMap.get(skuA), fittingNeed.getOrDefault(skuA, 0), asmQty);
        if (skuB != null && !skuB.equals(skuA)) {
            missingCost += appendFittingLine(vo, amounts, "B端接头", skuB,
                fittingMap.get(skuB), fittingNeed.getOrDefault(skuB, 0), asmQty);
        } else if (skuA != null) {
            // 两端同款：只出一行，但需求写成 2×根数，免得师傅照着单子只拿一个
            vo.getLines().get(vo.getLines().size() - 1).setRole("两端接头（同款）");
        }

        // ---------- 扣压外套 ----------
        HoseFerruleVo ferrule = hoseFerruleMapper.selectOneFor(layerScope, spec.getBoreCode(), skin);
        int ferruleNeed = 2 * asmQty;
        HoseQuoteLineVo fl = new HoseQuoteLineVo();
        fl.setRole("扣压外套");
        fl.setNeedText(ferruleNeed + " 个");
        if (ferrule == null) {
            fl.setCode("—");
            fl.setName(String.format("%s %s %s 用外套", layerScope, spec.getNickname(), skin));
            fl.setStatus(ST_NO_RECORD);
            fl.setStockText("档案里没有这一档");
            fl.setLocationText("—");
            vo.getBlockers().add(String.format("没有 %s %s %s 的外套档案", layerScope, spec.getNickname(), skin));
            missingCost++;
        } else {
            fl.setCode(ferrule.getFerruleSku());
            fl.setName(ferrule.getFerruleName());
            fl.setSpec(ferrule.getSkinType());
            fl.setLocationText(locationText(ferrule.getLocationCode(), ferrule.getLocationName()));
            applyCountable(fl, ferrule.getQty(), ferruleNeed);
            fl.setUnitCost(ferrule.getCostPrice());
            if (ferrule.getCostPrice() != null) {
                BigDecimal amt = ferrule.getCostPrice().multiply(BigDecimal.valueOf(ferruleNeed))
                    .setScale(2, RoundingMode.HALF_UP);
                fl.setAmount(amt);
                amounts.add(amt);
            } else {
                missingCost++;
            }
            collectStatus(vo, fl, "外套 " + ferrule.getFerruleSku());
        }
        vo.getLines().add(fl);

        // ---------- 扣压参数 / 能不能在店里压 ----------
        HoseCrimpVo crimp = hoseCrimpMapper.selectOneFor(layerScope, spec.getBoreCode());
        vo.setCrimp(crimp);
        boolean machineBlocked = crimp != null && Integer.valueOf(0).equals(crimp.getShopCanCrimp());
        if (machineBlocked) {
            vo.getBlockers().add(String.format("%s %s 这一档店里压机压不动，标记为要去仓库压",
                layerScope, spec.getNickname()));
        }

        boolean ok = vo.getBlockers().isEmpty();
        vo.setCanCrimp(ok);
        // 压不了就一律指向仓库——柜台该说的话是「报价 XX，去仓库压」
        vo.setGoWarehouse(!ok);
        vo.setVerdict(ok ? "可现场压" : (machineBlocked && vo.getBlockers().size() == 1 ? "去仓库压" : "缺料压不了"));

        vo.setSteps(buildSteps(spec, crimp, need, skin, asmQty));

        // ---------- 报价 ----------
        BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        vo.setCostTotal(total);
        vo.setMissingCostCount(missingCost);
        vo.setSellMarkup(SELL_MARKUP);
        vo.setSellPrice(markup(total, SELL_MARKUP));
        if (missingCost > 0) {
            vo.getWarnings().add(String.format(
                "有 %d 项还没录进价，报价只算了录了价的部分，是不全的", missingCost));
        }

        vo.setSummary(buildSummary(vo, spec, need, asmQty));
        return vo;
    }

    /** 接头行。返回缺成本价的项数（0 或 1） */
    private int appendFittingLine(HoseQuoteVo vo, List<BigDecimal> amounts, String role,
                                  String sku, HoseFittingVo f, int needQty, int asmQty) {
        HoseQuoteLineVo line = new HoseQuoteLineVo();
        line.setRole(role);
        line.setNeedText(needQty + " 个");
        vo.getLines().add(line);

        if (sku == null) {
            line.setCode("—");
            line.setName("没选");
            line.setStatus(ST_NO_RECORD);
            line.setStockText("—");
            line.setLocationText("—");
            vo.getBlockers().add(role + "没选，选了才能算料和报价");
            return 1;
        }
        if (f == null) {
            line.setCode(sku);
            line.setName("档案里没有这个接头");
            line.setStatus(ST_NO_RECORD);
            line.setStockText("—");
            line.setLocationText("—");
            vo.getBlockers().add("接头 " + sku + " 不在档案里");
            return 1;
        }
        line.setCode(f.getFittingSku());
        line.setName(f.getFieldName());
        line.setSpec(String.format("%s %s型（%s）%s%s",
            f.getThreadSpec(), f.getSeatType(), StrUtil.blankToDefault(f.getSealStd(), "密封形式待确认"),
            f.getAngle(), f.getGender()));
        line.setLocationText(locationText(f.getLocationCode(), f.getLocationName()));
        applyCountable(line, f.getQty(), needQty);
        line.setUnitCost(f.getCostPrice());
        collectStatus(vo, line, "接头 " + f.getFieldName());
        if (f.getCostPrice() != null) {
            BigDecimal amt = f.getCostPrice().multiply(BigDecimal.valueOf(needQty))
                .setScale(2, RoundingMode.HALF_UP);
            line.setAmount(amt);
            amounts.add(amt);
            return 0;
        }
        return 1;
    }

    /** 可数件（接头/外套）的库存判定。qty 为 null 是「还没盘」，不当缺料，但要提醒 */
    private void applyCountable(HoseQuoteLineVo line, Integer qty, int need) {
        if (qty == null) {
            line.setStockText("还没盘过");
            line.setStatus(ST_UNCOUNTED);
        } else if (qty >= need) {
            line.setStockText(qty + " 个");
            line.setStatus(ST_OK);
        } else {
            line.setStockText(qty + " 个，差 " + (need - qty) + " 个");
            line.setStatus(ST_SHORT);
        }
    }

    private void collectStatus(HoseQuoteVo vo, HoseQuoteLineVo line, String what) {
        if (ST_SHORT.equals(line.getStatus())) {
            vo.getBlockers().add(what + " 不够：" + line.getStockText());
        } else if (ST_UNCOUNTED.equals(line.getStatus())) {
            vo.getWarnings().add(what + " 还没盘过库存，去货架上确认一下再答复客户");
        }
    }

    /**
     * 层数码 → 外套/扣压参数用的层数档。
     * 三层管外径跟一二层不同，外套不通用，所以 03 单独一档。
     */
    private String layerScopeOf(String layerCode) {
        if (layerCode == null) {
            return "1层/2层";
        }
        return switch (layerCode) {
            case "01", "02" -> "1层/2层";
            case "03" -> "3层";
            case "04" -> "4层";
            case "06" -> "6层";
            default -> "1层/2层";
        };
    }

    private List<String> buildSteps(HoseSpecVo spec, HoseCrimpVo c, BigDecimal need,
                                    String skin, int asmQty) {
        String strip = param(c == null ? null : c.getStripLengthMm(), "mm");
        String insert = param(c == null ? null : c.getInsertDepthMm(), "mm");
        String dia = param(c == null ? null : c.getCrimpDiameterMm(), "mm");
        String die = param(c == null ? null : c.getDieNo(), "");
        String gear = param(c == null ? null : c.getPressGear(), "");

        List<String> s = new ArrayList<>();
        s.add(String.format("量长度：%s 米按两端接头密封面之间的距离算，弯头从弯头出口算起。裁之前再核一遍，短了整根报废。",
            trim(need)));
        s.add(String.format("裁管：切割片垂直下刀，切口要平不要斜。切完把胶屑和铁屑吹干净——留在管里会磨坏阀和泵。%s",
            asmQty > 1 ? "这单要 " + asmQty + " 根，一次量好一次裁。" : ""));
        if (DEFAULT_SKIN.equals(skin)) {
            s.add("剥胶：这单用非剥皮式外套，不用剥外胶，直接下一步。");
        } else {
            s.add(String.format("剥胶：剥皮式外套要剥，剥胶长度 %s。只剥外胶层，钢丝层不能剥断也不能剥伤。", strip));
        }
        s.add("套外套：外套先套到管头上，推到底。方向别装反，喇叭口朝管子那头。");
        s.add(String.format("插芯子：芯子外面抹一点液压油好插，插到底——插入深度 %s。插不到位压完必漏。", insert));
        s.add(String.format("上模具：换 %s 号模具，压机调到 %s 档。", die, gear));
        s.add(String.format("压：压到扣压直径 %s。一次压到位，不要回压第二次。", dia));
        s.add(String.format("复查：卡尺量扣压直径，公差 ±0.1mm；再看外套有没有压偏、管子有没有被压伤。%s",
            TBD.equals(dia) ? "扣压直径还没实测过，第一根压完量出来的值记回系统。" : ""));
        s.add(spec.getWorkPressureMpa() == null
            ? "试压：这个规格没有标准工作压力（22通径和三层管都不在欧标序列里），按厂家样本上的值打压，保压 1 分钟不渗不鼓。两头再吹一遍。"
            : String.format("试压：打压到 %s MPa 保压 1 分钟，接头处不渗不鼓。两头再吹一遍。",
                trim(spec.getWorkPressureMpa())));
        if (spec.getBendRadiusMm() != null) {
            s.add(String.format("交代客户：这根管最小弯曲半径 %d mm，装的时候弯得比这个急会爆。",
                spec.getBendRadiusMm()));
        }
        s.add(String.format("挂标签：写上 %s、长度 %s 米、日期。", spec.getHoseCode(), trim(need)));
        return s;
    }

    /**
     * 一句话结论。这句在页面上是常显的，**不能带进价** —— 客户站在柜台边上看得见。
     * 只说售价，进价要点星星才亮。
     */
    private String buildSummary(HoseQuoteVo vo, HoseSpecVo spec, BigDecimal need, int asmQty) {
        String head = String.format("%s %s %s，%s 米 × %d 根",
            spec.getNickname(), spec.getInch(), spec.getLayerName(), trim(need), asmQty);
        String money = vo.getMissingCostCount() != null && vo.getMissingCostCount() > 0
            ? String.format("售价至少 %s 元（还有 %d 项没录价，实际要更高）",
                vo.getSellPrice(), vo.getMissingCostCount())
            : String.format("售价 %s 元", vo.getSellPrice());
        if (Boolean.TRUE.equals(vo.getCanCrimp())) {
            return head + "：料齐，现场就能压。" + money + "。";
        }
        return head + "：" + String.join("；", vo.getBlockers()) + "。" + money + "，去仓库压。";
    }

    private String locationText(String code, String name) {
        if (StrUtil.isBlank(code)) {
            return "库位没填";
        }
        return StrUtil.isBlank(name) ? code : code + " " + name;
    }

    private static final String TBD = "待实测";

    private String param(Object v, String unit) {
        if (v == null || StrUtil.isBlank(String.valueOf(v))) {
            return TBD;
        }
        String text = v instanceof BigDecimal ? trim((BigDecimal) v) : String.valueOf(v);
        return StrUtil.isBlank(unit) ? text : text + " " + unit;
    }

    private String trim(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString();
    }

    private BigDecimal markup(BigDecimal cost, BigDecimal rate) {
        if (cost == null) {
            return null;
        }
        // 报价取整到元，柜台上不报角分
        return cost.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }

    // ============================================================
    // 扣压参数回填
    //
    // 这不是库存，是机器设置（模具号/扣压直径/剥胶长度/插入深度/压机能不能压），
    // 只能现场实测，所以留在本页面直接改。
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public int saveCrimp(List<HoseCrimp> list) {
        int n = 0;
        for (HoseCrimp c : list) {
            n += hoseCrimpMapper.updateById(c);
        }
        return n;
    }

    // ============================================================
    // 胶管分段增删改
    //
    // 分段表和 wms_inventory.quantity 必须同增同减：quantity 是「一共多少米」，
    // 分段表是「这些米怎么分布」。只改一边，配料查询就会拿旧数判断。
    // 所以下面每个写方法都在同一个事务里动两张表。
    // ============================================================

    /** 把某个 SKU 的库存数量重算成分段之和。分段是唯一事实来源 */
    private void syncInventory(Long skuId) {
        if (skuId == null) {
            return;
        }
        BigDecimal total = hosePieceMapper.sumInStock(skuId);
        BigDecimal v = total == null ? BigDecimal.ZERO : total;
        if (hosePieceMapper.updateInventoryQty(skuId, v) == 0) {
            hosePieceMapper.insertInventory(skuId, v);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addPiece(HosePiece piece) {
        if (StrUtil.isBlank(piece.getHoseCode())) {
            throw new ServiceException("请选择胶管规格");
        }
        if (piece.getLengthM() == null || piece.getLengthM().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("长度要大于 0");
        }
        piece.setStatus(StrUtil.blankToDefault(piece.getStatus(), "在库"));
        HoseSpecVo spec = hoseSpecMapper.selectOneWithStock(piece.getHoseCode());
        if (spec == null) {
            throw new ServiceException("没有这个胶管规格：" + piece.getHoseCode());
        }
        piece.setSkuId(spec.getSkuId());
        hosePieceMapper.insert(piece);
        syncInventory(piece.getSkuId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePiece(HosePiece piece) {
        if (piece.getId() == null) {
            throw new ServiceException("主键不能为空");
        }
        hosePieceMapper.updateById(piece);
        HosePiece after = hosePieceMapper.selectById(piece.getId());
        if (after != null) {
            syncInventory(after.getSkuId());
        }
    }

    /**
     * 裁走一段：从某段上切掉 usedM 米。剩余为 0 就标记用完，不物理删除，
     * 免得盘错了没法回溯。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cutPiece(Long pieceId, BigDecimal usedM) {
        HosePiece p = hosePieceMapper.selectById(pieceId);
        if (p == null) {
            throw new ServiceException("这一段不存在");
        }
        if (usedM == null || usedM.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("裁走的长度要大于 0");
        }
        if (usedM.compareTo(p.getLengthM()) > 0) {
            throw new ServiceException(String.format("这一段只有 %s 米，裁不出 %s 米",
                trim(p.getLengthM()), trim(usedM)));
        }
        BigDecimal left = p.getLengthM().subtract(usedM);
        HosePiece u = new HosePiece();
        u.setId(pieceId);
        u.setLengthM(left);
        if (left.compareTo(BigDecimal.ZERO) == 0) {
            u.setStatus("已用完");
        }
        hosePieceMapper.updateById(u);
        syncInventory(p.getSkuId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePiece(Long id) {
        HosePiece p = hosePieceMapper.selectById(id);
        hosePieceMapper.deleteById(id);
        if (p != null) {
            syncInventory(p.getSkuId());
        }
    }
}
