#Requires -Version 5.1
<#
.SYNOPSIS
    把本地（店里）MySQL 数据库单向同步到云端数据库。

.DESCRIPTION
    单向覆盖：本地 -> 云端。执行后云端数据库的内容会被本地数据完整替换。
    覆盖前会先把云端现有数据导出到本地一份，作为回退用的安全备份。

    连接信息从 backend\.env 读取，需要在其中配置 CLOUD_MYSQL_* 系列变量。

.PARAMETER Force
    跳过交互确认。仅用于你已经确认过流程之后的重复执行。
#>
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "威特仓库管理系统 - 同步到云端"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$EnvFile = Join-Path $RootDir "backend\.env"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        throw "找不到环境变量文件: $Path"
    }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $parts = $line.Split("=", 2)
        if ($parts.Count -ne 2) { return }
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Get-RequiredEnv {
    param([string]$Name, [string]$Hint)
    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "backend\.env 中缺少 $Name。$Hint"
    }
    return $value
}

function Get-EnvOrDefault {
    param([string]$Name, [string]$Default)
    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value
}

function Resolve-MysqlTool {
    param([string]$Name)
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $cmd) { return $cmd.Source }
    # MySQL 官方安装包默认不把 bin 加进 PATH，这里兜底找一下常见位置
    $candidates = Get-ChildItem "C:\Program Files\MySQL" -Filter "$Name.exe" -Recurse -ErrorAction SilentlyContinue
    if ($candidates) { return $candidates[0].FullName }
    throw "找不到 $Name。请把 MySQL 的 bin 目录加入 PATH，通常是 C:\Program Files\MySQL\MySQL Server 8.x\bin"
}

# 用 Start-Process 而不是管道，保证是原始字节重定向，不会被 PowerShell 的文本编码破坏
function Invoke-Tool {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$Password,
        [string]$StdOutFile,
        [string]$StdInFile,
        [string]$ErrorMessage
    )
    $errFile = [System.IO.Path]::GetTempFileName()
    $previousPwd = $env:MYSQL_PWD
    try {
        # 密码通过 MYSQL_PWD 传递，避免出现在命令行参数和进程列表里
        $env:MYSQL_PWD = $Password
        $params = @{
            FilePath     = $FilePath
            ArgumentList = $Arguments
            NoNewWindow  = $true
            Wait         = $true
            PassThru     = $true
            RedirectStandardError = $errFile
        }
        if ($StdOutFile) { $params.RedirectStandardOutput = $StdOutFile }
        if ($StdInFile) { $params.RedirectStandardInput = $StdInFile }
        $process = Start-Process @params
        if ($process.ExitCode -ne 0) {
            $detail = if (Test-Path $errFile) { (Get-Content $errFile -Raw).Trim() } else { "未知错误" }
            throw "$ErrorMessage`n$detail"
        }
        # mysqldump 可能成功退出但仍有警告，这里只在出错时才关心 stderr
    } finally {
        $env:MYSQL_PWD = $previousPwd
        Remove-Item $errFile -ErrorAction SilentlyContinue
    }
}

function Get-TableCount {
    param([string]$MysqlCmd, [hashtable]$Conn)
    $out = [System.IO.Path]::GetTempFileName()
    try {
        Invoke-Tool -FilePath $MysqlCmd -Password $Conn.Password -StdOutFile $out `
            -Arguments @(
                "--host=$($Conn.Host)", "--port=$($Conn.Port)", "--user=$($Conn.User)",
                "--ssl-mode=$($Conn.SslMode)", "--batch", "--skip-column-names",
                "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$($Conn.Database)'"
            ) `
            -ErrorMessage "连接 $($Conn.Host) 失败"
        return (Get-Content $out -Raw).Trim()
    } finally {
        Remove-Item $out -ErrorAction SilentlyContinue
    }
}

# ---------- 主流程 ----------

Write-Step "读取配置"
Import-DotEnv $EnvFile

$local = @{
    Host     = Get-EnvOrDefault "MYSQL_HOST" "localhost"
    Port     = Get-EnvOrDefault "MYSQL_PORT" "3306"
    Database = Get-RequiredEnv "MYSQL_DATABASE" ""
    User     = Get-RequiredEnv "MYSQL_USERNAME" ""
    Password = Get-RequiredEnv "MYSQL_PASSWORD" ""
    SslMode  = "PREFERRED"
}
$cloud = @{
    Host     = Get-RequiredEnv "CLOUD_MYSQL_HOST" "填写云服务器的地址。"
    Port     = Get-EnvOrDefault "CLOUD_MYSQL_PORT" "3306"
    Database = Get-EnvOrDefault "CLOUD_MYSQL_DATABASE" $null
    User     = Get-RequiredEnv "CLOUD_MYSQL_USERNAME" "填写云端数据库账号。"
    Password = Get-RequiredEnv "CLOUD_MYSQL_PASSWORD" "填写云端数据库密码。"
    # 数据要走公网，默认强制 TLS
    SslMode  = Get-EnvOrDefault "CLOUD_MYSQL_SSL_MODE" "REQUIRED"
}
if ([string]::IsNullOrWhiteSpace($cloud.Database)) { $cloud.Database = $local.Database }

if ($cloud.Host -eq $local.Host -and $cloud.Port -eq $local.Port -and $cloud.Database -eq $local.Database) {
    throw "云端和本地指向同一个数据库，拒绝执行。请检查 backend\.env 中的 CLOUD_MYSQL_* 配置。"
}

$Mysqldump = Resolve-MysqlTool "mysqldump"
$Mysql = Resolve-MysqlTool "mysql"

Write-Host "本地（源）: $($local.User)@$($local.Host):$($local.Port)/$($local.Database)"
Write-Host "云端（目标）: $($cloud.User)@$($cloud.Host):$($cloud.Port)/$($cloud.Database)  TLS=$($cloud.SslMode)"

Write-Step "检查两端连接"
$localTables = Get-TableCount -MysqlCmd $Mysql -Conn $local
$cloudTables = Get-TableCount -MysqlCmd $Mysql -Conn $cloud
Write-Host "本地表数量: $localTables"
Write-Host "云端表数量: $cloudTables（将被覆盖）"

if (-not $Force) {
    Write-Host ""
    Write-Host "即将用本地数据完整覆盖云端数据库 $($cloud.Database)。" -ForegroundColor Yellow
    Write-Host "云端现有数据会先备份到本地，但覆盖本身不可撤销。" -ForegroundColor Yellow
    $answer = Read-Host "确认请输入云端数据库名 [$($cloud.Database)]"
    if ($answer -ne $cloud.Database) {
        Write-Host "已取消。" -ForegroundColor Yellow
        exit 0
    }
}

$backupDir = Get-EnvOrDefault "WMS_BACKUP_DIR" (Join-Path $RootDir "backups")
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

$dumpArgs = @(
    "--single-transaction", "--routines", "--events", "--triggers", "--hex-blob",
    "--add-drop-table", "--no-tablespaces", "--set-gtid-purged=OFF",
    "--default-character-set=utf8mb4"
)

Write-Step "备份云端现有数据（回退用）"
$cloudBackup = Join-Path $backupDir "cloud-before-sync-$stamp.sql"
if ([int]$cloudTables -gt 0) {
    Invoke-Tool -FilePath $Mysqldump -Password $cloud.Password -StdOutFile $cloudBackup `
        -Arguments ($dumpArgs + @(
            "--host=$($cloud.Host)", "--port=$($cloud.Port)", "--user=$($cloud.User)",
            "--ssl-mode=$($cloud.SslMode)", $cloud.Database
        )) `
        -ErrorMessage "导出云端数据失败"
    Write-Host "已保存: $cloudBackup"
} else {
    Write-Host "云端为空库，跳过。"
}

Write-Step "导出本地数据"
$localDump = Join-Path $backupDir "local-sync-$stamp.sql"
Invoke-Tool -FilePath $Mysqldump -Password $local.Password -StdOutFile $localDump `
    -Arguments ($dumpArgs + @(
        "--host=$($local.Host)", "--port=$($local.Port)", "--user=$($local.User)",
        $local.Database
    )) `
    -ErrorMessage "导出本地数据失败"
$sizeMb = [math]::Round((Get-Item $localDump).Length / 1MB, 2)
Write-Host "已导出: $localDump（$sizeMb MB）"

Write-Step "写入云端"
Invoke-Tool -FilePath $Mysql -Password $cloud.Password -StdInFile $localDump `
    -Arguments @(
        "--host=$($cloud.Host)", "--port=$($cloud.Port)", "--user=$($cloud.User)",
        "--ssl-mode=$($cloud.SslMode)", "--default-character-set=utf8mb4", $cloud.Database
    ) `
    -ErrorMessage "写入云端失败。云端可能处于半更新状态，可用 $cloudBackup 回退。"

Write-Step "校验"
$after = Get-TableCount -MysqlCmd $Mysql -Conn $cloud
Write-Host "云端表数量: $after（本地为 $localTables）"
if ($after -ne $localTables) {
    Write-Host "表数量不一致，请检查上面的输出。" -ForegroundColor Yellow
} else {
    Write-Host "同步完成。" -ForegroundColor Green
}

Remove-Item $localDump -ErrorAction SilentlyContinue
