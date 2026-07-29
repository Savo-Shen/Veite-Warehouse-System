package com.ruoyi.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * 外部 MySQL 数据库备份。密码通过 MYSQL_PWD 环境变量传给 mysqldump，避免出现在命令行参数中。
 */
@Slf4j
@Service
public class DbBackupService {

    private static final Pattern BACKUP_NAME = Pattern.compile("wms-\\d{8}-\\d{6}\\.sql\\.gz");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.of("Asia/Shanghai"));

    private final Path backupDir;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${MYSQL_HOST:localhost}")
    private String host;
    @Value("${MYSQL_PORT:3306}")
    private String port;
    @Value("${MYSQL_DATABASE:ry-vue}")
    private String database;
    @Value("${MYSQL_USERNAME:root}")
    private String username;
    @Value("${MYSQL_PASSWORD:}")
    private String password;

    public DbBackupService(@Value("${wms.database-backup.dir:/app/backups}") String backupDir) {
        this.backupDir = Path.of(backupDir).toAbsolutePath().normalize();
    }

    public List<Map<String, Object>> list() {
        ensureDirectory();
        try (Stream<Path> files = Files.list(backupDir)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> BACKUP_NAME.matcher(path.getFileName().toString()).matches())
                .sorted(Comparator.comparing(Path::getFileName).reversed())
                .map(this::fileInfo)
                .toList();
        } catch (Exception e) {
            throw new IllegalStateException("读取备份列表失败", e);
        }
    }

    public Map<String, Object> create() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有备份任务正在执行，请稍后再试");
        }
        String filename = "wms-" + FILE_TIME.format(Instant.now()) + ".sql.gz";
        Path raw = backupDir.resolve(filename + ".sql.part");
        Path compressed = backupDir.resolve(filename + ".part");
        Path target = backupDir.resolve(filename);
        Path errorFile = backupDir.resolve(filename + ".err");
        try {
            ensureDirectory();
            ProcessBuilder builder = new ProcessBuilder(
                "mysqldump", "--host=" + host, "--port=" + port, "--user=" + username,
                "--single-transaction", "--routines", "--events", "--triggers", "--hex-blob",
                "--default-character-set=utf8mb4", database
            );
            builder.environment().put("MYSQL_PWD", password);
            builder.redirectOutput(raw.toFile());
            builder.redirectError(errorFile.toFile());
            Process process = builder.start();
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("数据库导出超时");
            }
            if (process.exitValue() != 0) {
                String detail = Files.exists(errorFile) ? Files.readString(errorFile, StandardCharsets.UTF_8) : "未知错误";
                throw new IllegalStateException("mysqldump 执行失败：" + detail.trim());
            }
            try (InputStream input = Files.newInputStream(raw);
                 OutputStream output = new GZIPOutputStream(Files.newOutputStream(compressed))) {
                input.transferTo(output);
            }
            Files.move(compressed, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("数据库备份完成：{}", target);
            return fileInfo(target);
        } catch (Exception e) {
            log.error("数据库备份失败", e);
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            running.set(false);
            deleteQuietly(raw);
            deleteQuietly(compressed);
            deleteQuietly(errorFile);
        }
    }

    public Path resolve(String filename) {
        validateName(filename);
        Path path = backupDir.resolve(filename).normalize();
        if (!path.startsWith(backupDir) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("备份文件不存在");
        }
        return path;
    }

    public void delete(String filename) {
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (Exception e) {
            throw new IllegalStateException("删除备份失败", e);
        }
    }

    private Map<String, Object> fileInfo(Path path) {
        try {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", path.getFileName().toString());
            info.put("size", Files.size(path));
            info.put("lastModified", Files.getLastModifiedTime(path).toInstant().toString());
            return info;
        } catch (Exception e) {
            throw new IllegalStateException("读取备份文件信息失败", e);
        }
    }

    private void validateName(String filename) {
        if (filename == null || !BACKUP_NAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("非法备份文件名");
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(backupDir);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建备份目录：" + backupDir, e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("清理临时备份文件失败：{}", path, e);
        }
    }
}
