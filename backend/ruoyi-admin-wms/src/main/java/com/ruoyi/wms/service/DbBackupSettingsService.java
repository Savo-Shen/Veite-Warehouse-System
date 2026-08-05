package com.ruoyi.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wms.domain.vo.DbBackupSettingsVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份设置的读写。设置存在 sys_config 里（键名前缀 wms.backup.），后台页面可以直接改，改完立即生效、重启不丢。
 * <p>
 * sys_config 里没有的项用 application.yml / 环境变量里的值兜底，所以老版本升级上来不用做任何迁移。
 *
 * @author savo
 */
@Slf4j
@Service
public class DbBackupSettingsService {

    private static final String KEY_ENABLED = "wms.backup.enabled";
    private static final String KEY_MODE = "wms.backup.mode";
    private static final String KEY_INTERVAL_HOURS = "wms.backup.intervalHours";
    private static final String KEY_DAILY_TIME = "wms.backup.dailyTime";
    private static final String KEY_WEEKDAYS = "wms.backup.weekdays";
    private static final String KEY_RETENTION_DAYS = "wms.backup.retentionDays";
    private static final String KEY_MIN_KEEP = "wms.backup.minKeep";
    private static final String KEY_DIR = "wms.backup.dir";
    private static final String KEY_MIRROR_DIR = "wms.backup.mirrorDir";
    private static final String KEY_MYSQLDUMP = "wms.backup.mysqldumpPath";
    private static final String KEY_LAST_RUN = "wms.backup.lastRun";

    /** sys_config.config_value 是 varchar(500)，写入前按这个长度截断，避免报错丢掉整条记录 */
    private static final int VALUE_LIMIT = 480;

    private static final Map<String, String> CONFIG_NAMES = Map.ofEntries(
        Map.entry(KEY_ENABLED, "数据库备份-是否自动备份"),
        Map.entry(KEY_MODE, "数据库备份-计划模式"),
        Map.entry(KEY_INTERVAL_HOURS, "数据库备份-间隔小时"),
        Map.entry(KEY_DAILY_TIME, "数据库备份-每天备份时刻"),
        Map.entry(KEY_WEEKDAYS, "数据库备份-备份星期"),
        Map.entry(KEY_RETENTION_DAYS, "数据库备份-保留天数"),
        Map.entry(KEY_MIN_KEEP, "数据库备份-最少保留份数"),
        Map.entry(KEY_DIR, "数据库备份-备份目录"),
        Map.entry(KEY_MIRROR_DIR, "数据库备份-离机副本目录"),
        Map.entry(KEY_MYSQLDUMP, "数据库备份-mysqldump 路径"),
        Map.entry(KEY_LAST_RUN, "数据库备份-上次执行结果")
    );

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /** 只用来序列化「上次执行结果」这一个字段，自己 new 一个，不依赖 Spring 容器里的 ObjectMapper */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    // ===== application.yml / 环境变量里的默认值，仅在 sys_config 中还没有对应记录时使用 =====
    @Value("${wms.database-backup.enabled:true}")
    private boolean defaultEnabled;
    @Value("${wms.database-backup.interval-hours:24}")
    private int defaultIntervalHours;
    @Value("${wms.database-backup.retention-days:14}")
    private int defaultRetentionDays;
    @Value("${wms.database-backup.min-keep:3}")
    private int defaultMinKeep;
    @Value("${wms.database-backup.dir:./backups}")
    private String defaultDir;
    @Value("${wms.database-backup.mirror-dir:}")
    private String defaultMirrorDir;
    @Value("${wms.database-backup.mysqldump-path:}")
    private String defaultMysqldumpPath;

    public DbBackupSettingsService(DataSource dataSource) {
        // 与 DbAlignService 一致：直接基于主数据源构建，不依赖 JdbcTemplate 自动装配
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** 当前生效的设置。数据库读不到时退回默认值，保证调度器不会因为一次查询失败而停摆。 */
    public DbBackupSettingsVo current() {
        DbBackupSettingsVo settings = defaults();
        Map<String, String> stored;
        try {
            stored = load();
        } catch (Exception e) {
            log.warn("读取备份设置失败，本次使用默认配置", e);
            return settings;
        }
        settings.setEnabled(bool(stored.get(KEY_ENABLED), settings.isEnabled()));
        settings.setMode(mode(stored.get(KEY_MODE), settings.getMode()));
        settings.setIntervalHours(number(stored.get(KEY_INTERVAL_HOURS), settings.getIntervalHours()));
        settings.setDailyTime(time(stored.get(KEY_DAILY_TIME), settings.getDailyTime()));
        settings.setWeekdays(weekdays(stored.get(KEY_WEEKDAYS), settings.getWeekdays()));
        settings.setRetentionDays(number(stored.get(KEY_RETENTION_DAYS), settings.getRetentionDays()));
        settings.setMinKeep(number(stored.get(KEY_MIN_KEEP), settings.getMinKeep()));
        settings.setDir(text(stored.get(KEY_DIR), settings.getDir()));
        settings.setMirrorDir(stored.containsKey(KEY_MIRROR_DIR)
            ? blankToEmpty(stored.get(KEY_MIRROR_DIR)) : settings.getMirrorDir());
        settings.setMysqldumpPath(stored.containsKey(KEY_MYSQLDUMP)
            ? blankToEmpty(stored.get(KEY_MYSQLDUMP)) : settings.getMysqldumpPath());
        return settings;
    }

    /** 校验并保存设置，返回规整之后的结果。 */
    public DbBackupSettingsVo save(DbBackupSettingsVo input) {
        DbBackupSettingsVo settings = validate(input);
        put(KEY_ENABLED, String.valueOf(settings.isEnabled()));
        put(KEY_MODE, settings.getMode());
        put(KEY_INTERVAL_HOURS, String.valueOf(settings.getIntervalHours()));
        put(KEY_DAILY_TIME, settings.getDailyTime());
        put(KEY_WEEKDAYS, join(settings.getWeekdays()));
        put(KEY_RETENTION_DAYS, String.valueOf(settings.getRetentionDays()));
        put(KEY_MIN_KEEP, String.valueOf(settings.getMinKeep()));
        put(KEY_DIR, settings.getDir());
        put(KEY_MIRROR_DIR, settings.getMirrorDir());
        put(KEY_MYSQLDUMP, settings.getMysqldumpPath());
        log.info("备份设置已更新：{}", settings);
        return settings;
    }

    /** 记录一次备份结果，供页面展示。失败原因也存进去，重启后依然看得到。 */
    public void recordRun(boolean success, String trigger, String message, String filename, Long size, long elapsedMs) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("success", success);
        run.put("trigger", trigger);
        run.put("time", Instant.now().toString());
        run.put("message", message == null ? "" : message.replace('\n', ' ').replace('\r', ' ').trim());
        run.put("filename", filename);
        run.put("size", size);
        run.put("elapsedMs", elapsedMs);
        try {
            put(KEY_LAST_RUN, MAPPER.writeValueAsString(run));
        } catch (Throwable e) {
            log.warn("记录备份结果失败", e);
        }
    }

    /** 上次备份结果，从未执行过时返回 null。 */
    public Map<String, Object> lastRun() {
        try {
            String value = load().get(KEY_LAST_RUN);
            if (value == null || value.isBlank()) {
                return null;
            }
            return MAPPER.readValue(value, Map.class);
        } catch (Throwable e) {
            log.warn("读取上次备份结果失败", e);
            return null;
        }
    }

    // ===== 校验 =====

    /**
     * 校验并规整输入。目录会当场创建并试写一次，配错路径在「保存」时就报错，而不是等到半夜备份失败。
     */
    public DbBackupSettingsVo validate(DbBackupSettingsVo input) {
        if (input == null) {
            throw new IllegalArgumentException("设置内容为空");
        }
        DbBackupSettingsVo settings = new DbBackupSettingsVo();
        settings.setEnabled(input.isEnabled());

        String mode = input.getMode() == null ? "" : input.getMode().trim();
        if (!DbBackupSettingsVo.MODE_INTERVAL.equals(mode) && !DbBackupSettingsVo.MODE_DAILY.equals(mode)) {
            throw new IllegalArgumentException("备份计划模式只能是 interval 或 daily");
        }
        settings.setMode(mode);

        if (input.getIntervalHours() < 1 || input.getIntervalHours() > 24 * 365) {
            throw new IllegalArgumentException("备份间隔必须在 1 到 8760 小时之间");
        }
        settings.setIntervalHours(input.getIntervalHours());

        String dailyTime = input.getDailyTime() == null ? "" : input.getDailyTime().trim();
        try {
            settings.setDailyTime(HH_MM.format(LocalTime.parse(dailyTime, HH_MM)));
        } catch (Exception e) {
            throw new IllegalArgumentException("备份时刻格式不正确，应形如 03:00");
        }

        List<Integer> weekdays = new ArrayList<>();
        if (input.getWeekdays() != null) {
            for (Integer day : input.getWeekdays()) {
                if (day == null || day < 1 || day > 7) {
                    throw new IllegalArgumentException("星期取值只能是 1-7（1 为周一）");
                }
                if (!weekdays.contains(day)) {
                    weekdays.add(day);
                }
            }
        }
        weekdays.sort(Integer::compareTo);
        if (DbBackupSettingsVo.MODE_DAILY.equals(mode) && weekdays.isEmpty()) {
            throw new IllegalArgumentException("每天定时模式至少要选择一个星期");
        }
        settings.setWeekdays(weekdays);

        if (input.getRetentionDays() < 0 || input.getRetentionDays() > 3650) {
            throw new IllegalArgumentException("保留天数必须在 0 到 3650 之间（0 表示不清理）");
        }
        settings.setRetentionDays(input.getRetentionDays());

        if (input.getMinKeep() < 1 || input.getMinKeep() > 1000) {
            throw new IllegalArgumentException("最少保留份数必须在 1 到 1000 之间");
        }
        settings.setMinKeep(input.getMinKeep());

        String dir = blankToEmpty(input.getDir());
        if (dir.isEmpty()) {
            throw new IllegalArgumentException("备份目录不能为空");
        }
        settings.setDir(checkWritable(dir, "备份目录").toString());

        String mirrorDir = blankToEmpty(input.getMirrorDir());
        settings.setMirrorDir(mirrorDir.isEmpty() ? "" : checkWritable(mirrorDir, "离机副本目录").toString());

        settings.setMysqldumpPath(blankToEmpty(input.getMysqldumpPath()));
        return settings;
    }

    /** 目录能创建并且能写才算可用，返回规整后的绝对路径。 */
    private Path checkWritable(String value, String label) {
        Path dir;
        try {
            dir = Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(label + "不是合法路径：" + value);
        }
        Path probe = dir.resolve(".wms-write-test");
        try {
            Files.createDirectories(dir);
            Files.writeString(probe, "ok");
            return dir;
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "无法写入：" + dir + "（" + e.getMessage() + "）");
        } finally {
            try {
                Files.deleteIfExists(probe);
            } catch (Exception ignored) {
                // 探针文件删不掉不影响判断结果
            }
        }
    }

    // ===== sys_config 读写 =====

    private Map<String, String> load() {
        Map<String, String> values = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT config_key, config_value FROM sys_config WHERE config_key LIKE 'wms.backup.%'");
        for (Map<String, Object> row : rows) {
            Object key = row.get("config_key");
            Object value = row.get("config_value");
            if (key != null) {
                values.put(key.toString(), value == null ? "" : value.toString());
            }
        }
        return values;
    }

    private void put(String key, String value) {
        String stored = value == null ? "" : value;
        if (stored.length() > VALUE_LIMIT) {
            stored = stored.substring(0, VALUE_LIMIT);
        }
        int updated = jdbcTemplate.update(
            "UPDATE sys_config SET config_value = ?, update_by = 'system', update_time = NOW() WHERE config_key = ?",
            stored, key);
        if (updated > 0) {
            return;
        }
        Long id = jdbcTemplate.queryForObject("SELECT IFNULL(MAX(config_id), 0) + 1 FROM sys_config", Long.class);
        jdbcTemplate.update("""
            INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_by, create_time, remark)
            VALUES (?, ?, ?, ?, 'Y', 'system', NOW(), '由「数据库备份」页面维护，不建议在此直接修改')
            """, id, CONFIG_NAMES.getOrDefault(key, key), key, stored);
    }

    // ===== 解析辅助 =====

    private DbBackupSettingsVo defaults() {
        DbBackupSettingsVo settings = new DbBackupSettingsVo();
        settings.setEnabled(defaultEnabled);
        settings.setMode(DbBackupSettingsVo.MODE_INTERVAL);
        settings.setIntervalHours(defaultIntervalHours);
        settings.setRetentionDays(defaultRetentionDays);
        settings.setMinKeep(defaultMinKeep);
        settings.setDir(Path.of(blankToEmpty(defaultDir).isEmpty() ? "./backups" : defaultDir)
            .toAbsolutePath().normalize().toString());
        settings.setMirrorDir(blankToEmpty(defaultMirrorDir));
        settings.setMysqldumpPath(blankToEmpty(defaultMysqldumpPath));
        return settings;
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String mode(String value, String fallback) {
        String mode = value == null ? "" : value.trim();
        return DbBackupSettingsVo.MODE_INTERVAL.equals(mode) || DbBackupSettingsVo.MODE_DAILY.equals(mode)
            ? mode : fallback;
    }

    private static String time(String value, String fallback) {
        try {
            return HH_MM.format(LocalTime.parse(value.trim(), HH_MM));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<Integer> weekdays(String value, List<Integer> fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        List<Integer> days = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                int day = Integer.parseInt(part.trim());
                if (day >= 1 && day <= 7 && !days.contains(day)) {
                    days.add(day);
                }
            } catch (Exception ignored) {
                // 忽略脏数据
            }
        }
        days.sort(Integer::compareTo);
        return days.isEmpty() ? fallback : days;
    }

    private static String join(List<Integer> days) {
        return days == null ? "" : days.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
