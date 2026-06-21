package com.ruoyi.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 数据库对齐：检测并补齐新版本才有的表/字段/菜单（幂等，可重复执行）。
 *
 * @author savo
 */
@Slf4j
@Service
public class DbAlignService {

    private final JdbcTemplate jdbcTemplate;

    public DbAlignService(DataSource dataSource) {
        // 基于主数据源构建，避免依赖 JdbcTemplate 自动装配
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    // ====== DDL 定义 ======
    private static final String CREATE_ITEM_TAG = """
        CREATE TABLE IF NOT EXISTS `wms_item_tag` (
          `id` bigint(20) NOT NULL AUTO_INCREMENT,
          `tag_name` varchar(30) NOT NULL COMMENT '标签名称',
          `color` varchar(20) NULL DEFAULT '#409EFF' COMMENT '标签颜色',
          `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
          `create_by` varchar(64) NULL DEFAULT NULL,
          `create_time` datetime(3) NULL DEFAULT NULL,
          `update_by` varchar(64) NULL DEFAULT NULL,
          `update_time` datetime(3) NULL DEFAULT NULL,
          PRIMARY KEY (`id`) USING BTREE,
          UNIQUE INDEX `uk_tag_name`(`tag_name`) USING BTREE
        ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品标签表'
        """;

    private static final String CREATE_ITEM_TAG_REL = """
        CREATE TABLE IF NOT EXISTS `wms_item_tag_rel` (
          `id` bigint(20) NOT NULL AUTO_INCREMENT,
          `item_id` bigint(20) NOT NULL COMMENT '商品ID',
          `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
          `create_by` varchar(64) NULL DEFAULT NULL,
          `create_time` datetime(3) NULL DEFAULT NULL,
          `update_by` varchar(64) NULL DEFAULT NULL,
          `update_time` datetime(3) NULL DEFAULT NULL,
          PRIMARY KEY (`id`) USING BTREE,
          UNIQUE INDEX `uk_item_tag`(`item_id`, `tag_id`) USING BTREE,
          INDEX `idx_item_id`(`item_id`) USING BTREE,
          INDEX `idx_tag_id`(`tag_id`) USING BTREE
        ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品标签关联表'
        """;

    private static final String ADD_LOCATION_WAREHOUSE = "ALTER TABLE `wms_location` ADD COLUMN `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '所属仓库' AFTER `location_code`";
    private static final String ADD_WAREHOUSE_SHELF_LAYOUT = "ALTER TABLE `wms_warehouse` ADD COLUMN `shelf_layout` longtext NULL DEFAULT NULL COMMENT '货架3D布局(JSON)' AFTER `remark`";

    private static final String MENU_TAG_M = "INSERT INTO `sys_menu` VALUES (1940000000000000001, '标签管理', 1808758090157985794, 5, 'itemTag', 'wms/basic/itemTag/index', NULL, 0, 0, 'C', '1', '1', 'wms:itemTag:list', 'documentation', 'admin', NOW(), 'admin', NOW(), '商品标签管理菜单')";
    private static final String MENU_TAG_Q = "INSERT INTO `sys_menu` VALUES (1940000000000000002, '标签查询', 1940000000000000001, 1, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:itemTag:list', '#', 'admin', NOW(), 'admin', NOW(), '')";
    private static final String MENU_TAG_E = "INSERT INTO `sys_menu` VALUES (1940000000000000003, '标签编辑', 1940000000000000001, 2, '', NULL, NULL, 0, 0, 'F', '1', '1', 'wms:itemTag:edit', '#', 'admin', NOW(), 'admin', NOW(), '')";
    private static final String MENU_DBALIGN = "INSERT INTO `sys_menu` VALUES (1940000000000000010, '数据库对齐', 1808758090157985794, 9, 'dbAlign', 'wms/tool/dbAlign/index', NULL, 0, 0, 'C', '1', '1', 'wms:tool:dbAlign', 'set-up', 'admin', NOW(), 'admin', NOW(), '数据库对齐工具')";

    private List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("商品标签表 (wms_item_tag)", "表", () -> tableExists("wms_item_tag"), List.of(CREATE_ITEM_TAG)));
        steps.add(new Step("商品标签关联表 (wms_item_tag_rel)", "表", () -> tableExists("wms_item_tag_rel"), List.of(CREATE_ITEM_TAG_REL)));
        steps.add(new Step("位置-所属仓库 (wms_location.warehouse_id)", "字段", () -> columnExists("wms_location", "warehouse_id"), List.of(ADD_LOCATION_WAREHOUSE)));
        steps.add(new Step("仓库-货架布局 (wms_warehouse.shelf_layout)", "字段", () -> columnExists("wms_warehouse", "shelf_layout"), List.of(ADD_WAREHOUSE_SHELF_LAYOUT)));
        steps.add(new Step("菜单-标签管理", "菜单", () -> menuExists(1940000000000000001L), List.of(MENU_TAG_M)));
        steps.add(new Step("菜单-标签查询", "菜单", () -> menuExists(1940000000000000002L), List.of(MENU_TAG_Q)));
        steps.add(new Step("菜单-标签编辑", "菜单", () -> menuExists(1940000000000000003L), List.of(MENU_TAG_E)));
        steps.add(new Step("菜单-数据库对齐", "菜单", () -> menuExists(1940000000000000010L), List.of(MENU_DBALIGN)));
        return steps;
    }

    /**
     * 检测各项是否已存在
     */
    public List<Map<String, Object>> check() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Step s : steps()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name);
            m.put("type", s.type);
            m.put("exists", s.exists.getAsBoolean());
            result.add(m);
        }
        return result;
    }

    /**
     * 执行对齐：仅补齐缺失项
     */
    public Map<String, Object> align() {
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<Map<String, String>> failed = new ArrayList<>();
        for (Step s : steps()) {
            if (s.exists.getAsBoolean()) {
                skipped.add(s.name);
                continue;
            }
            try {
                for (String sql : s.sqls) {
                    jdbcTemplate.execute(sql);
                }
                applied.add(s.name);
                log.info("数据库对齐：已补齐 {}", s.name);
            } catch (Exception e) {
                log.error("数据库对齐失败：{}", s.name, e);
                Map<String, String> f = new LinkedHashMap<>();
                f.put("name", s.name);
                f.put("error", e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applied", applied);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }

    private boolean tableExists(String table) {
        Integer c = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            Integer.class, table);
        return c != null && c > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer c = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
            Integer.class, table, column);
        return c != null && c > 0;
    }

    private boolean menuExists(Long menuId) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE menu_id = ?", Integer.class, menuId);
        return c != null && c > 0;
    }

    private static class Step {
        final String name;
        final String type;
        final BooleanSupplier exists;
        final List<String> sqls;

        Step(String name, String type, BooleanSupplier exists, List<String> sqls) {
            this.name = name;
            this.type = type;
            this.exists = exists;
            this.sqls = sqls;
        }
    }
}
