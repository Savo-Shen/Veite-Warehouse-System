package com.ruoyi.wms.service;

import com.ruoyi.wms.domain.vo.DbBackupSettingsVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
 * <p>
 * 和 MySQL 是否跑在 Docker 里无关：这里只是起一个 mysqldump 子进程去连 MYSQL_HOST，
 * 宿主机上的独立 MySQL 服务同样支持。前提是能找到 mysqldump 可执行文件（见 {@link #resolveMysqldump}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbBackupService {

    private static final Pattern BACKUP_NAME = Pattern.compile("wms-\\d{8}-\\d{6}\\.sql\\.gz");
    private static final String NAME_PREFIX = "wms-";
    private static final String NAME_SUFFIX = ".sql.gz";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZONE);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private final DbBackupSettingsService settingsService;
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

    public Path backupDir() {
        return dirOf(settingsService.current().getDir());
    }

    /** 离机副本目录，未配置时返回 null。 */
    public Path mirrorDir() {
        String mirror = settingsService.current().getMirrorDir();
        return mirror == null || mirror.isBlank() ? null : dirOf(mirror);
    }

    /** 是否有备份正在执行 */
    public boolean isRunning() {
        return running.get();
    }

    public List<Map<String, Object>> list() {
        Path dir = backupDir();
        ensureDirectory(dir);
        try (Stream<Path> files = Files.list(dir)) {
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
        return create("手动");
    }

    /**
     * 执行一次备份。无论成功失败都会把结果记到设置里，页面上能直接看到失败原因。
     *
     * @param trigger 触发方式，用于展示：手动 / 自动
     */
    public Map<String, Object> create(String trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有备份任务正在执行，请稍后再试");
        }
        DbBackupSettingsVo settings = settingsService.current();
        Path dir = dirOf(settings.getDir());
        String filename = NAME_PREFIX + FILE_TIME.format(Instant.now()) + NAME_SUFFIX;
        Path raw = dir.resolve(filename + ".sql.part");
        Path compressed = dir.resolve(filename + ".part");
        Path target = dir.resolve(filename);
        Path errorFile = dir.resolve(filename + ".err");
        long startedAt = System.currentTimeMillis();
        try {
            ensureDirectory(dir);
            String mysqldump = resolveMysqldump(settings.getMysqldumpPath());
            ProcessBuilder builder = new ProcessBuilder(
                mysqldump, "--host=" + host, "--port=" + port, "--user=" + username,
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
            String mirrorNote = mirror(target, settings.getMirrorDir());
            long size = Files.size(target);
            settingsService.recordRun(true, trigger, "备份成功" + mirrorNote, filename, size,
                System.currentTimeMillis() - startedAt);
            return fileInfo(target);
        } catch (Exception e) {
            log.error("数据库备份失败", e);
            settingsService.recordRun(false, trigger, e.getMessage(), null, null,
                System.currentTimeMillis() - startedAt);
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
        Path dir = backupDir();
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
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

    /** 最近一次备份的时间，目录中没有任何备份时返回 null。 */
    public Instant lastBackupTime() {
        Path dir = backupDir();
        ensureDirectory(dir);
        return backupNames(dir).stream().findFirst().map(DbBackupService::parseTime).orElse(null);
    }

    /**
     * 删除超过保留天数的备份，正本目录和副本目录一起清理。
     * 始终保留最近 minKeep 份，避免长期未开机时把仅有的备份清空。
     *
     * @return 实际删除的文件数
     */
    public int cleanupExpired(int retentionDays, int minKeep) {
        if (retentionDays <= 0) {
            return 0;
        }
        int removed = cleanupDirectory(backupDir(), retentionDays, minKeep);
        Path mirror = mirrorDir();
        if (mirror != null) {
            removed += cleanupDirectory(mirror, retentionDays, minKeep);
        }
        return removed;
    }

    // ===== 环境自检 =====

    /**
     * 逐项检查备份能不能跑起来：mysqldump 是否找得到、目录能不能写、账号能不能连上数据库。
     * 页面上点「环境自检」就能看到，不用去翻服务器日志。
     */
    public List<Map<String, Object>> checkEnvironment() {
        DbBackupSettingsVo settings = settingsService.current();
        List<Map<String, Object>> checks = new ArrayList<>();

        String mysqldump = null;
        try {
            mysqldump = resolveMysqldump(settings.getMysqldumpPath());
            String version = runAndCapture(List.of(mysqldump, "--version"), 30);
            checks.add(check("mysqldump 可执行文件", true, mysqldump + "（" + version.trim() + "）"));
        } catch (Exception e) {
            checks.add(check("mysqldump 可执行文件", false, e.getMessage()));
        }

        checks.add(directoryCheck("备份目录", settings.getDir()));
        if (settings.getMirrorDir() == null || settings.getMirrorDir().isBlank()) {
            checks.add(check("离机副本目录", false,
                "未配置。备份与数据库多半在同一块硬盘上，硬盘损坏会一起丢失，建议指向网盘同步目录或移动硬盘"));
        } else {
            checks.add(directoryCheck("离机副本目录", settings.getMirrorDir()));
        }

        if (mysqldump != null) {
            checks.add(connectionCheck(mysqldump));
        } else {
            checks.add(check("数据库连接（" + host + ":" + port + "/" + database + "）", false,
                "找不到 mysqldump，无法测试"));
        }
        return checks;
    }

    private Map<String, Object> directoryCheck(String label, String value) {
        Path probe = null;
        try {
            Path dir = dirOf(value);
            Files.createDirectories(dir);
            probe = dir.resolve(".wms-write-test");
            Files.writeString(probe, "ok");
            return check(label, true, dir.toString());
        } catch (Exception e) {
            return check(label, false, value + "：" + e.getMessage());
        } finally {
            deleteQuietly(probe);
        }
    }

    /** 用 mysqldump 只导结构、不导数据来验证账号和网络，比单纯 ping 更接近真实备份。 */
    private Map<String, Object> connectionCheck(String mysqldump) {
        String label = "数据库连接（" + host + ":" + port + "/" + database + "）";
        try {
            runAndCapture(List.of(mysqldump, "--host=" + host, "--port=" + port, "--user=" + username,
                "--no-data", "--skip-triggers", "--skip-routines", "--skip-events", database), 120);
            return check(label, true, "连接正常，账号 " + username + " 有导出权限");
        } catch (Exception e) {
            return check(label, false, e.getMessage());
        }
    }

    private static Map<String, Object> check(String name, boolean ok, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("ok", ok);
        item.put("detail", detail);
        return item;
    }

    /**
     * 执行命令并返回标准输出的开头部分，失败时抛出带 stderr 的异常。
     * <p>
     * 两个流都必须读干净：只读一部分就不读了的话，子进程会因为管道写满而卡死，最终变成假的「执行超时」。
     */
    private String runAndCapture(List<String> command, int timeoutSeconds) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("MYSQL_PWD", password);
        Process process = builder.start();
        StringBuilder error = new StringBuilder();
        Thread errorReader = new Thread(() -> {
            try (InputStream stderr = process.getErrorStream()) {
                error.append(drain(stderr, 8 * 1024));
            } catch (Exception ignored) {
                // 读不到 stderr 不影响主流程
            }
        });
        errorReader.setDaemon(true);
        errorReader.start();
        String output;
        try (InputStream stdout = process.getInputStream()) {
            output = drain(stdout, 4 * 1024);
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("命令执行超时：" + command.get(0));
        }
        errorReader.join(2000);
        if (process.exitValue() != 0) {
            String detail = error.toString().trim();
            throw new IllegalStateException(detail.isEmpty() ? "命令返回码 " + process.exitValue() : detail);
        }
        return output;
    }

    /** 读完整个流，但只保留开头 keep 个字节 */
    private static String drain(InputStream input, int keep) throws Exception {
        byte[] buffer = new byte[8 * 1024];
        byte[] head = new byte[keep];
        int kept = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int copy = Math.min(read, keep - kept);
            if (copy > 0) {
                System.arraycopy(buffer, 0, head, kept, copy);
                kept += copy;
            }
        }
        return new String(head, 0, kept, StandardCharsets.UTF_8);
    }

    // ===== mysqldump 定位 =====

    /**
     * 找到 mysqldump 可执行文件。
     * <p>
     * Windows 上 MySQL 安装程序默认不会把 bin 目录加进 PATH，这是备份最常见的失败原因，
     * 所以这里除了 PATH 之外还会去常见安装位置找一遍；仍找不到时可以在页面上直接填绝对路径。
     *
     * @param configured 页面上配置的路径，可为空
     */
    public String resolveMysqldump(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path path;
            try {
                path = Path.of(configured.trim());
            } catch (InvalidPathException e) {
                throw new IllegalStateException("配置的 mysqldump 路径不合法：" + configured);
            }
            // 允许只填 bin 目录
            if (Files.isDirectory(path)) {
                path = path.resolve(executableName());
            }
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("配置的 mysqldump 路径不存在：" + path);
            }
            return path.toString();
        }
        for (Path dir : candidateDirectories()) {
            Path candidate = dir.resolve(executableName());
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        throw new IllegalStateException("找不到 mysqldump。它随 MySQL 一起安装，Windows 上通常在 "
            + "C:\\Program Files\\MySQL\\MySQL Server 8.x\\bin。"
            + "请把该目录加入系统 PATH 后重启后端，或直接在本页「mysqldump 路径」里填写完整路径。");
    }

    private static String executableName() {
        return WINDOWS ? "mysqldump.exe" : "mysqldump";
    }

    /** PATH 里的目录 + 常见安装位置 */
    private static List<Path> candidateDirectories() {
        List<Path> dirs = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
                addDirectory(dirs, entry.replace("\"", "").trim());
            }
        }
        addDirectory(dirs, join(System.getenv("MYSQL_HOME"), "bin"));
        if (WINDOWS) {
            List<String> roots = new ArrayList<>(List.of("C:\\Program Files", "C:\\Program Files (x86)",
                "D:\\Program Files", "C:\\", "D:\\"));
            for (String var : List.of("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)")) {
                String value = System.getenv(var);
                if (value != null && !value.isBlank()) {
                    roots.add(value);
                }
            }
            for (String root : roots) {
                // C:\Program Files\MySQL\MySQL Server 8.4\bin
                addVersionedDirectories(dirs, join(root, "MySQL"), "MySQL Server");
                // C:\Program Files\MariaDB 11.4\bin
                addVersionedDirectories(dirs, root, "MariaDB");
                addDirectory(dirs, join(root, "xampp", "mysql", "bin"));
                addDirectory(dirs, join(root, "phpstudy_pro", "Extensions", "MySQL8.0.12", "bin"));
            }
        } else {
            for (String dir : List.of("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin",
                "/usr/local/mysql/bin", "/opt/homebrew/opt/mysql-client/bin")) {
                addDirectory(dirs, dir);
            }
        }
        return dirs;
    }

    /** 把 parent 下形如「prefix 8.4」的子目录中的 bin 加进候选，版本号大的排前面 */
    private static void addVersionedDirectories(List<Path> dirs, String parent, String prefix) {
        if (parent == null) {
            return;
        }
        Path base;
        try {
            base = Path.of(parent);
        } catch (InvalidPathException e) {
            return;
        }
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> children = Files.list(base)) {
            children.filter(Files::isDirectory)
                .filter(child -> child.getFileName().toString().startsWith(prefix))
                .sorted(Comparator.comparing((Path child) -> child.getFileName().toString()).reversed())
                .forEach(child -> addDirectory(dirs, child.resolve("bin").toString()));
        } catch (Exception ignored) {
            // 目录读不到就跳过
        }
    }

    private static void addDirectory(List<Path> dirs, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            Path dir = Path.of(value);
            if (!dirs.contains(dir)) {
                dirs.add(dir);
            }
        } catch (InvalidPathException ignored) {
            // PATH 里可能有非法条目，跳过即可
        }
    }

    private static String join(String first, String... more) {
        if (first == null || first.isBlank()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(first);
        for (String part : more) {
            builder.append(File.separator).append(part);
        }
        return builder.toString();
    }

    // ===== 内部工具 =====

    private int cleanupDirectory(Path dir, int retentionDays, int minKeep) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        List<String> names = backupNames(dir);
        Instant deadline = Instant.now().minus(Duration.ofDays(retentionDays));
        int removed = 0;
        for (int i = Math.max(minKeep, 0); i < names.size(); i++) {
            String name = names.get(i);
            if (!parseTime(name).isBefore(deadline)) {
                continue;
            }
            Path path = dir.resolve(name);
            try {
                Files.delete(path);
                removed++;
                log.info("已删除过期备份：{}", path);
            } catch (Exception e) {
                log.warn("删除过期备份失败：{}", path, e);
            }
        }
        return removed;
    }

    /** 目录内的备份文件名，按时间从新到旧排序。文件名宽度固定，字典序即时间序。 */
    private List<String> backupNames(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> BACKUP_NAME.matcher(name).matches())
                .sorted(Comparator.reverseOrder())
                .toList();
        } catch (Exception e) {
            log.warn("读取备份目录失败：{}", dir, e);
            return List.of();
        }
    }

    private static Instant parseTime(String filename) {
        String stamp = filename.substring(NAME_PREFIX.length(), filename.length() - NAME_SUFFIX.length());
        return LocalDateTime.parse(stamp, FILE_TIME).atZone(ZONE).toInstant();
    }

    /**
     * 把备份复制一份到离机目录（网盘同步盘、移动硬盘等）。失败只告警，不影响正本。
     *
     * @return 追加到结果里的说明文字
     */
    private String mirror(Path source, String mirrorDir) {
        if (mirrorDir == null || mirrorDir.isBlank()) {
            return "（未配置离机副本）";
        }
        Path dir = dirOf(mirrorDir);
        String filename = source.getFileName().toString();
        Path temporary = dir.resolve(filename + ".part");
        try {
            Files.createDirectories(dir);
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            Path target = dir.resolve(filename);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("备份副本已写入：{}", target);
            return "，副本已写入 " + dir;
        } catch (Exception e) {
            log.warn("写入备份副本失败：{}", dir, e);
            deleteQuietly(temporary);
            return "，但写入离机副本失败：" + e.getMessage();
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

    private static Path dirOf(String value) {
        String dir = value == null || value.isBlank() ? "./backups" : value.trim();
        try {
            return Path.of(dir).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalStateException("备份目录不是合法路径：" + dir);
        }
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建备份目录：" + dir, e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("清理临时备份文件失败：{}", path, e);
        }
    }
}
