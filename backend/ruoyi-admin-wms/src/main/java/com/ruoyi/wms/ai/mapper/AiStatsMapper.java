package com.ruoyi.wms.ai.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 统计聚合：按客户/商品/规格/分类/月/日汇总出入库明细。SQL 在 mapper/wms/AiStatsMapper.xml。
 *
 * @author Savo
 */
public interface AiStatsMapper {

    List<Map<String, Object>> selectStats(@Param("shipment") boolean shipment,
                                          @Param("groupBy") String groupBy,
                                          @Param("begin") LocalDate begin,
                                          @Param("end") LocalDate end,
                                          @Param("merchantIds") List<Long> merchantIds,
                                          @Param("skuIds") List<Long> skuIds,
                                          @Param("sort") String sort,
                                          @Param("limit") int limit);
}
