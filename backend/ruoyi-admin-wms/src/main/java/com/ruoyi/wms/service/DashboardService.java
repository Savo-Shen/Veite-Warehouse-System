package com.ruoyi.wms.service;

import com.ruoyi.wms.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库经营看板服务。
 */
@RequiredArgsConstructor
@Service
public class DashboardService {

    private static final int TREND_DAYS = 14;
    private static final int TOP_LIMIT = 8;

    private final DashboardMapper dashboardMapper;

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", dashboardMapper.selectSummary());
        result.put("dailyTrend", dashboardMapper.selectDailyTrend(TREND_DAYS));
        result.put("warehouseStock", dashboardMapper.selectWarehouseStock());
        result.put("topShipmentSku", dashboardMapper.selectTopShipmentSku(30, TOP_LIMIT));
        result.put("lowStockSku", dashboardMapper.selectLowStockSku(TOP_LIMIT));
        return result;
    }
}
