package com.ruoyi.wms.service;

import com.ruoyi.wms.domain.vo.DbBackupSettingsVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动数据库备份。
 * <p>
 * 店里的机器晚上会关机，固定时刻的 cron 很可能永远不触发，所以这里用补偿式策略：
 * 每几分钟检查一次「按计划本该备份的那个时间点过去了没、之后有没有备份过」，缺了就立刻补一次。
 * 定时模式下即使 3 点没开机，早上开机后也会马上补上当天的备份。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbBackupScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /** 定时模式往前找计划时间点的最大天数，覆盖「一周只备份一天」的配置 */
    private static final int LOOKBACK_DAYS = 8;

    private final DbBackupService dbBackupService;
    private final DbBackupSettingsService settingsService;

    @Scheduled(
        initialDelayString = "${wms.database-backup.initial-delay-ms:120000}",
        fixedDelayString = "${wms.database-backup.check-interval-ms:300000}"
    )
    public void autoBackup() {
        DbBackupSettingsVo settings = settingsService.current();
        if (!settings.isEnabled()) {
            return;
        }
        try {
            Instant last = dbBackupService.lastBackupTime();
            if (isDue(settings, last, Instant.now())) {
                log.info("触发自动备份，上次备份时间：{}", last == null ? "无" : last);
                dbBackupService.create("自动");
            }
        } catch (Exception e) {
            // 备份失败不能中断调度，下个检查周期会重试；失败原因已记录在设置里，页面上能看到
            log.error("自动备份失败", e);
        }
        try {
            dbBackupService.cleanupExpired(settings.getRetentionDays(), settings.getMinKeep());
        } catch (Exception e) {
            log.warn("清理过期备份失败", e);
        }
    }

    /** 启动后做一次环境自检，把结果写进日志，配置有问题不用等到半夜才发现。 */
    @EventListener(ApplicationReadyEvent.class)
    public void selfCheckOnStartup() {
        try {
            DbBackupSettingsVo settings = settingsService.current();
            if (!settings.isEnabled()) {
                log.info("数据库自动备份未开启");
                return;
            }
            for (Map<String, Object> item : dbBackupService.checkEnvironment()) {
                if (Boolean.TRUE.equals(item.get("ok"))) {
                    log.info("备份自检 [通过] {}：{}", item.get("name"), item.get("detail"));
                } else {
                    log.warn("备份自检 [异常] {}：{}", item.get("name"), item.get("detail"));
                }
            }
        } catch (Exception e) {
            log.warn("备份环境自检失败", e);
        }
    }

    /** 给后台页面展示当前配置、上次结果和下次计划时间。 */
    public Map<String, Object> status() {
        DbBackupSettingsVo settings = settingsService.current();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", settings.isEnabled());
        status.put("mode", settings.getMode());
        status.put("intervalHours", settings.getIntervalHours());
        status.put("dailyTime", settings.getDailyTime());
        status.put("weekdays", settings.getWeekdays());
        status.put("retentionDays", settings.getRetentionDays());
        status.put("minKeep", settings.getMinKeep());
        status.put("running", dbBackupService.isRunning());
        Instant last = null;
        try {
            status.put("dir", dbBackupService.backupDir().toString());
            Path mirror = dbBackupService.mirrorDir();
            status.put("mirrorDir", mirror == null ? null : mirror.toString());
            last = dbBackupService.lastBackupTime();
        } catch (Exception e) {
            log.warn("读取备份目录状态失败", e);
            status.put("dir", settings.getDir());
            status.put("mirrorDir", settings.getMirrorDir());
        }
        status.put("lastBackupTime", last == null ? null : last.toString());
        status.put("nextRunTime", settings.isEnabled() ? nextRun(settings, last, Instant.now()) : null);
        status.put("lastRun", settingsService.lastRun());
        return status;
    }

    // ===== 计划计算 =====

    /** 现在是否该备份 */
    static boolean isDue(DbBackupSettingsVo settings, Instant last, Instant now) {
        if (DbBackupSettingsVo.MODE_DAILY.equals(settings.getMode())) {
            Instant scheduled = lastScheduled(settings, now);
            // 找不到计划时间点（例如刚配好、这周还没轮到）就先不备份
            return scheduled != null && (last == null || last.isBefore(scheduled));
        }
        return last == null || !last.plus(Duration.ofHours(settings.getIntervalHours())).isAfter(now);
    }

    /** 定时模式下「最近一个已经过去的计划时间点」，往前找一周还没有就返回 null */
    private static Instant lastScheduled(DbBackupSettingsVo settings, Instant now) {
        LocalTime time = parseTime(settings.getDailyTime());
        LocalDateTime local = LocalDateTime.ofInstant(now, ZONE);
        for (int i = 0; i < LOOKBACK_DAYS; i++) {
            LocalDate date = local.toLocalDate().minusDays(i);
            if (!settings.getWeekdays().contains(date.getDayOfWeek().getValue())) {
                continue;
            }
            LocalDateTime candidate = LocalDateTime.of(date, time);
            if (!candidate.isAfter(local)) {
                return candidate.atZone(ZONE).toInstant();
            }
        }
        return null;
    }

    /** 下一次计划备份时间，仅用于页面展示 */
    private static String nextRun(DbBackupSettingsVo settings, Instant last, Instant now) {
        if (DbBackupSettingsVo.MODE_DAILY.equals(settings.getMode())) {
            if (isDue(settings, last, now)) {
                return "尽快（本次计划时间已过且尚未备份）";
            }
            LocalTime time = parseTime(settings.getDailyTime());
            LocalDateTime local = LocalDateTime.ofInstant(now, ZONE);
            for (int i = 0; i <= LOOKBACK_DAYS; i++) {
                LocalDate date = local.toLocalDate().plusDays(i);
                if (!settings.getWeekdays().contains(date.getDayOfWeek().getValue())) {
                    continue;
                }
                LocalDateTime candidate = LocalDateTime.of(date, time);
                if (candidate.isAfter(local)) {
                    return candidate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                }
            }
            return null;
        }
        if (last == null) {
            return "尽快（还没有任何备份）";
        }
        Instant next = last.plus(Duration.ofHours(settings.getIntervalHours()));
        if (!next.isAfter(now)) {
            return "尽快（已超过备份间隔）";
        }
        return LocalDateTime.ofInstant(next, ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, HH_MM);
        } catch (Exception e) {
            return LocalTime.of(3, 0);
        }
    }
}
