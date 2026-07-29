package com.ruoyi.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * WMS 数据库合并导入：先导入临时库，再按主键比较 wms_* 业务表。
 * 系统用户表不直接覆盖；create_by/update_by 按用户名映射。
 */
@Slf4j
@Service
public class DbImportService {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> ALLOWED_PREFIXES = Set.of("wms_");
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final Path importDir;
    private final Map<String, ImportContext> sessions = new ConcurrentHashMap<>();

    @Value("${MYSQL_HOST:localhost}")
    private String mysqlHost;
    @Value("${MYSQL_PORT:3306}")
    private String mysqlPort;
    @Value("${MYSQL_USERNAME:root}")
    private String mysqlUsername;
    @Value("${MYSQL_PASSWORD:}")
    private String mysqlPassword;

    public DbImportService(DataSource dataSource,
                           @Value("${wms.database-import.dir:/app/backups/imports}") String importDir) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.importDir = Path.of(importDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择数据库备份文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(original.endsWith(".sql") || original.endsWith(".sql.gz"))) {
            throw new IllegalArgumentException("只支持 .sql 或 .sql.gz 文件");
        }
        String id = ID_TIME.format(java.time.LocalDateTime.now()) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String database = "wms_import_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            Files.createDirectories(importDir);
            Path dump = importDir.resolve(id + (original.endsWith(".gz") ? ".sql.gz" : ".sql"));
            file.transferTo(dump);
            validateDump(dump, original.endsWith(".gz"));
            createDatabase(database);
            importDump(dump, database, original.endsWith(".gz"));
            ImportContext context = new ImportContext(id, database, dump);
            sessions.put(id, context);
            return Map.of("sessionId", id, "filename", file.getOriginalFilename(), "preview", preview(id));
        } catch (Exception e) {
            dropDatabase(database);
            throw new IllegalStateException("导入文件解析失败：" + rootMessage(e), e);
        }
    }

    public Map<String, Object> preview(String sessionId) {
        ImportContext context = context(sessionId);
        List<Map<String, Object>> tables = new ArrayList<>();
        for (String table : importTables(context.database)) {
            tables.add(tablePreview(context.database, table));
        }
        return Map.of(
            "sessionId", sessionId,
            "tables", tables,
            "users", userPreview(context.database),
            "targetUsers", targetUsernames(),
            "warning", "只导入 wms_* 业务表；用户、角色、权限不会直接覆盖当前系统。"
        );
    }

    @Transactional
    public Map<String, Object> apply(String sessionId, Map<String, Object> request) {
        ImportContext context = context(sessionId);
        Map<String, String> userMapping = readUserMapping(request.get("users"));
        Map<String, String> tableStrategy = readTableStrategy(request.get("tables"));
        List<Map<String, Object>> results = new ArrayList<>();
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            for (String table : importTables(context.database)) {
                String strategy = tableStrategy.getOrDefault(table, "skip");
                if ("ignore".equals(strategy)) {
                    continue;
                }
                results.add(mergeTable(context.database, table, strategy, userMapping));
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        cleanup(sessionId);
        return Map.of("tables", results, "message", "数据库合并导入完成");
    }

    public void cancel(String sessionId) {
        context(sessionId);
        cleanup(sessionId);
    }

    private Map<String, Object> mergeTable(String sourceDb, String table, String strategy, Map<String, String> userMapping) {
        List<String> columns = columns(sourceDb, table);
        List<String> keys = primaryKeys(sourceDb, table);
        if (keys.isEmpty()) {
            return Map.of("table", table, "status", "skipped", "message", "没有主键，无法安全判断重复数据");
        }
        if (!tableExists(databaseName(), table)) {
            return Map.of("table", table, "status", "skipped", "message", "正式库不存在此表，请先执行数据库对齐");
        }
        Map<String, Map<String, Object>> target = keyedRows(databaseName(), table, columns, keys);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (Map<String, Object> sourceRow : rows(sourceDb, table, columns)) {
            Map<String, Object> row = rewriteUserFields(sourceRow, userMapping);
            String key = rowKey(row, keys);
            Map<String, Object> existing = target.get(key);
            if (existing == null) {
                insert(table, columns, row);
                inserted++;
            } else if (sameRow(existing, row, columns) || "skip".equals(strategy)) {
                skipped++;
            } else if ("replace".equals(strategy)) {
                update(table, columns, keys, row);
                updated++;
            }
        }
        return Map.of("table", table, "status", "ok", "inserted", inserted, "updated", updated, "skipped", skipped);
    }

    private Map<String, Object> tablePreview(String sourceDb, String table) {
        List<String> columns = columns(sourceDb, table);
        List<String> keys = primaryKeys(sourceDb, table);
        long sourceCount = count(sourceDb, table);
        if (!tableExists(databaseName(), table)) {
            return Map.of("name", table, "sourceCount", sourceCount, "targetCount", 0, "newCount", sourceCount, "conflictCount", 0, "status", "missing_target_table");
        }
        if (keys.isEmpty()) {
            return Map.of("name", table, "sourceCount", sourceCount, "targetCount", count(databaseName(), table), "newCount", 0, "conflictCount", 0, "status", "no_primary_key");
        }
        Map<String, Map<String, Object>> target = keyedRows(databaseName(), table, columns, keys);
        int newCount = 0;
        int conflictCount = 0;
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (Map<String, Object> row : rows(sourceDb, table, columns)) {
            Map<String, Object> existing = target.get(rowKey(row, keys));
            if (existing == null) {
                newCount++;
            } else if (!sameRow(existing, row, columns)) {
                conflictCount++;
                if (conflicts.size() < 20) {
                    conflicts.add(Map.of("key", rowKey(row, keys), "source", safeRow(row), "target", safeRow(existing)));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", table);
        result.put("sourceCount", sourceCount);
        result.put("targetCount", count(databaseName(), table));
        result.put("newCount", newCount);
        result.put("conflictCount", conflictCount);
        result.put("conflicts", conflicts);
        result.put("status", "ok");
        return result;
    }

    private List<Map<String, Object>> userPreview(String sourceDb) {
        if (!tableExists(sourceDb, "sys_user")) return List.of();
        Map<String, Long> target = new HashMap<>();
        if (tableExists(databaseName(), "sys_user")) {
            for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT user_id, user_name FROM `sys_user`")) {
                target.put(String.valueOf(row.get("user_name")), ((Number) row.get("user_id")).longValue());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT user_id, user_name FROM `" + sourceDb + "`.`sys_user` ORDER BY user_id")) {
            String username = String.valueOf(row.get("user_name"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceUserId", row.get("user_id"));
            item.put("sourceUsername", username);
            item.put("targetUserId", target.get(username));
            item.put("targetUsername", target.containsKey(username) ? username : "");
            item.put("status", target.containsKey(username) ? "matched" : "unmatched");
            result.add(item);
        }
        return result;
    }

    private List<String> targetUsernames() {
        if (!tableExists(databaseName(), "sys_user")) return List.of();
        return jdbcTemplate.queryForList("SELECT user_name FROM `sys_user` ORDER BY user_name", String.class);
    }

    private Map<String, Object> safeRow(Map<String, Object> row) {
        Map<String, Object> safe = new LinkedHashMap<>();
        row.forEach((key, value) -> safe.put(key, jsonValue(value)));
        return safe;
    }

    private Object jsonValue(Object value) {
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> rewriteUserFields(Map<String, Object> source, Map<String, String> mapping) {
        Map<String, Object> row = new LinkedHashMap<>(source);
        for (String column : List.of("create_by", "update_by")) {
            if (row.containsKey(column) && row.get(column) != null) {
                String original = String.valueOf(row.get(column));
                row.put(column, mapping.getOrDefault(original, original));
            }
        }
        return row;
    }

    private Map<String, String> readUserMapping(Object value) {
        Map<String, String> result = new HashMap<>();
        if (!(value instanceof List<?> list)) return result;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String source = string(map.get("sourceUsername"));
            String target = string(map.get("targetUsername"));
            if (!source.isBlank() && !target.isBlank()) result.put(source, target);
        }
        return result;
    }

    private Map<String, String> readTableStrategy(Object value) {
        Map<String, String> result = new HashMap<>();
        if (!(value instanceof List<?> list)) return result;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String table = string(map.get("name"));
            String strategy = string(map.get("strategy"));
            if (!table.isBlank() && Set.of("skip", "replace", "ignore").contains(strategy)) result.put(table, strategy);
        }
        return result;
    }

    private List<String> importTables(String db) {
        return jdbcTemplate.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name", String.class, db)
            .stream().filter(name -> ALLOWED_PREFIXES.stream().anyMatch(name::startsWith)).toList();
    }

    private List<String> columns(String db, String table) {
        return jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position", String.class, db, table);
    }

    private List<String> primaryKeys(String db, String table) {
        return jdbcTemplate.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema = ? AND table_name = ? AND constraint_name = 'PRIMARY' ORDER BY ordinal_position", String.class, db, table);
    }

    private List<Map<String, Object>> rows(String db, String table, List<String> columns) {
        return jdbcTemplate.queryForList("SELECT " + identifiers(columns) + " FROM `" + db + "`.`" + identifier(table) + "`", new Object[0]);
    }

    private Map<String, Map<String, Object>> keyedRows(String db, String table, List<String> columns, List<String> keys) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows(db, table, columns)) result.put(rowKey(row, keys), row);
        return result;
    }

    private long count(String db, String table) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + db + "`.`" + identifier(table) + "`", Long.class);
        return result == null ? 0 : result;
    }

    private boolean tableExists(String db, String table) {
        Integer result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?", Integer.class, db, table);
        return result != null && result > 0;
    }

    private boolean sameRow(Map<String, Object> a, Map<String, Object> b, List<String> columns) {
        for (String column : columns) if (!Objects.equals(normalize(a.get(column)), normalize(b.get(column)))) return false;
        return true;
    }

    private String rowKey(Map<String, Object> row, List<String> keys) {
        return keys.stream().map(key -> String.valueOf(normalize(row.get(key)))).reduce((a, b) -> a + "|" + b).orElse("");
    }

    private Object normalize(Object value) {
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        return value == null ? null : String.valueOf(value);
    }

    private void insert(String table, List<String> columns, Map<String, Object> row) {
        String marks = String.join(",", Collections.nCopies(columns.size(), "?"));
        jdbcTemplate.update("INSERT INTO `" + identifier(table) + "` (" + identifiers(columns) + ") VALUES (" + marks + ")", columns.stream().map(row::get).toArray());
    }

    private void update(String table, List<String> columns, List<String> keys, Map<String, Object> row) {
        List<String> nonKeys = columns.stream().filter(column -> !keys.contains(column)).toList();
        if (nonKeys.isEmpty()) return;
        String assignments = nonKeys.stream().map(column -> "`" + identifier(column) + "`=?").reduce((a, b) -> a + "," + b).orElse("");
        String where = keys.stream().map(key -> "`" + identifier(key) + "`=?").reduce((a, b) -> a + " AND " + b).orElse("");
        List<Object> args = new ArrayList<>();
        nonKeys.forEach(column -> args.add(row.get(column)));
        keys.forEach(key -> args.add(row.get(key)));
        jdbcTemplate.update("UPDATE `" + identifier(table) + "` SET " + assignments + " WHERE " + where, args.toArray());
    }

    private void createDatabase(String database) {
        jdbcTemplate.execute("CREATE DATABASE `" + identifier(database) + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
    }

    private void importDump(Path dump, String database, boolean gzip) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", gzip
            ? "gzip -dc \"$DUMP_FILE\" | mysql --host=\"$MYSQL_HOST\" --port=\"$MYSQL_PORT\" --user=\"$MYSQL_USERNAME\" \"$IMPORT_DB\""
            : "mysql --host=\"$MYSQL_HOST\" --port=\"$MYSQL_PORT\" --user=\"$MYSQL_USERNAME\" \"$IMPORT_DB\" < \"$DUMP_FILE\"");
        builder.environment().put("DUMP_FILE", dump.toString());
        builder.environment().put("IMPORT_DB", database);
        builder.environment().put("MYSQL_HOST", mysqlHost);
        builder.environment().put("MYSQL_PORT", mysqlPort);
        builder.environment().put("MYSQL_USERNAME", mysqlUsername);
        builder.environment().put("MYSQL_PWD", mysqlPassword);
        Process process = builder.start();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("临时库导入超时");
        }
        if (process.exitValue() != 0) {
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("临时库导入失败：" + error);
        }
    }

    private void validateDump(Path dump, boolean gzip) throws Exception {
        try (var input = gzip ? new GZIPInputStream(Files.newInputStream(dump)) : Files.newInputStream(dump);
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lines = 0;
            while ((line = reader.readLine()) != null && lines++ < 5000) {
                String upper = line.trim().toUpperCase();
                if (upper.startsWith("CREATE DATABASE") || upper.startsWith("DROP DATABASE") || upper.startsWith("USE ")) {
                    throw new IllegalArgumentException("不支持包含 CREATE/DROP DATABASE 或 USE 的整库脚本，请使用系统导出的 .sql.gz");
                }
            }
        }
    }

    private ImportContext context(String id) {
        ImportContext context = sessions.get(id);
        if (context == null) throw new IllegalArgumentException("导入会话不存在或已过期");
        return context;
    }

    private void cleanup(String id) {
        ImportContext context = sessions.remove(id);
        if (context == null) return;
        dropDatabase(context.database);
        try { Files.deleteIfExists(context.dump); } catch (Exception e) { log.warn("清理导入文件失败：{}", context.dump, e); }
    }

    private void dropDatabase(String database) {
        try { jdbcTemplate.execute("DROP DATABASE IF EXISTS `" + identifier(database) + "`"); } catch (Exception e) { log.warn("清理临时数据库失败：{}", database, e); }
    }

    private String databaseName() {
        return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    }

    private static String identifiers(List<String> names) {
        return names.stream().map(name -> "`" + identifier(name) + "`").reduce((a, b) -> a + "," + b).orElse("");
    }

    private static String identifier(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("非法数据库对象名");
        return name;
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String rootMessage(Exception e) { return e.getCause() == null ? e.getMessage() : e.getCause().getMessage(); }

    private record ImportContext(String id, String database, Path dump) {}
}
