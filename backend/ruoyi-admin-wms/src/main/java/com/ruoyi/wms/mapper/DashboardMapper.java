package com.ruoyi.wms.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 仓库经营看板 Mapper。
 */
public interface DashboardMapper {

    Map<String, Object> selectSummary();

    List<Map<String, Object>> selectDailyTrend(@Param("days") Integer days);

    List<Map<String, Object>> selectWarehouseStock();

    List<Map<String, Object>> selectTopShipmentSku(@Param("days") Integer days, @Param("limit") Integer limit);

    List<Map<String, Object>> selectLowStockSku(@Param("limit") Integer limit);
}
