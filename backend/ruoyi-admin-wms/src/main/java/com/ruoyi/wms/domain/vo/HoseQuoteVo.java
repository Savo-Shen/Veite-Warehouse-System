package com.ruoyi.wms.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 配料查询结果：接一根总成要什么料、料在哪、怎么压、大概多少钱。
 * <p>
 * 三种结局：
 *   可现场压   —— 料齐、压机也压得动，给位置 + 教程 + 报价
 *   去仓库压   —— 料齐但店里压机压不动这一档，给报价并让去仓库
 *   缺料压不了 —— 少东西，照样给报价（要报给客户），并说清少什么
 * 无论哪种都出报价，这是这个页面最常用的功能。
 *
 * @author savo
 * @date 2026-08-23
 */
@Data
public class HoseQuoteVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 可现场压 / 去仓库压 / 缺料压不了 */
    private String verdict;

    /** 店里能不能直接压出来 */
    private Boolean canCrimp;

    /** 要不要去仓库压 */
    private Boolean goWarehouse;

    /** 一句话结论，直接念给客户听 */
    private String summary;

    /** 压不了的硬原因 */
    private List<String> blockers = new ArrayList<>();

    /** 不拦路但要注意的，比如「这批还没盘过库存」 */
    private List<String> warnings = new ArrayList<>();

    /** 配料单，正常 5 行 */
    private List<HoseQuoteLineVo> lines = new ArrayList<>();

    /** 够长的胶管段，按从短到长排 —— 优先切最短的够用段，省长料 */
    private List<HosePieceVo> usablePieces = new ArrayList<>();

    /** 该规格全部在库段，用来解释「合计够但单段不够」 */
    private List<HosePieceVo> allPieces = new ArrayList<>();

    /** 扣压参数，没实测过就是一堆 null */
    private HoseCrimpVo crimp;

    /** 压管步骤，已按本次规格把参数填进去 */
    private List<String> steps = new ArrayList<>();

    /**
     * 进价合计（只算得出单价的项）。
     * 敏感字段：页面上默认不显示，跟手机查价页一样点星星才亮出来。
     */
    private BigDecimal costTotal;

    /** 还有几项没录进价 —— 大于 0 时报价是不全的，前端要标出来 */
    private Integer missingCostCount;

    /** 售价 = 进价 × 2，柜台直接报这个数 */
    private BigDecimal sellPrice;

    /** 加价倍数，前端要显示「进价×2」时用，改倍数只动 service 里的常量 */
    private BigDecimal sellMarkup;

    /** 做几根 */
    private Integer assemblyQty;
}
