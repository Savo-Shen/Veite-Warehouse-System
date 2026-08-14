$ErrorActionPreference = "Stop"

$Host.UI.RawUI.WindowTitle = "威特仓库管理系统 - 正式启动"

if ($env:VEITE_WMS_SCRIPT_DIR) {
    $ScriptDir = $env:VEITE_WMS_SCRIPT_DIR
} else {
    $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
}
$BackendDir = Join-Path $ScriptDir "backend"
$FrontendDir = Join-Path $ScriptDir "frontend-Vue"
$EnvFile = Join-Path $BackendDir ".env"
$BackendJar = Join-Path $BackendDir "ruoyi-admin-wms\target\ruoyi-admin-wms.jar"

$BackendProcess = $null
$FrontendProcess = $null
$BackendStarted = $false
$FrontendStarted = $false
$Stopping = $false

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    Write-Host "加载 backend\.env 环境变量..."
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line.Split("=", 2)
        if ($parts.Count -ne 2) {
            return
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Resolve-CommandPath {
    param(
        [string[]]$Names,
        [string]$InstallHint
    )

    foreach ($name in $Names) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $cmd) {
            return $cmd.Source
        }
    }

    throw "找不到命令: $($Names -join ' / ')。请先安装或配置 PATH: $InstallHint"
}

function Get-NewestSourceWriteTime {
    param([string]$Path)

    # 只看会被打进 Jar 的源码类文件，避免改 .env、日志等触发无谓的重新打包
    $sourceExtensions = @(".java", ".xml", ".yml", ".yaml", ".properties", ".sql", ".ftl")
    $skipDirectories = @("target", ".git", ".idea", "node_modules", "logs")

    $newest = [DateTime]::MinValue
    $pending = New-Object System.Collections.Stack
    $pending.Push($Path)

    while ($pending.Count -gt 0) {
        $current = $pending.Pop()
        foreach ($entry in Get-ChildItem -LiteralPath $current -Force -ErrorAction SilentlyContinue) {
            if ($entry.PSIsContainer) {
                if ($skipDirectories -notcontains $entry.Name) {
                    $pending.Push($entry.FullName)
                }
            } elseif ($sourceExtensions -contains $entry.Extension.ToLower() -and $entry.LastWriteTime -gt $newest) {
                $newest = $entry.LastWriteTime
            }
        }
    }

    return $newest
}

function Invoke-BackendPackage {
    param(
        [string]$BackendDir,
        [bool]$JarExists
    )

    try {
        $MvnCmd = Resolve-CommandPath -Names @("mvn.cmd", "mvn") -InstallHint "Maven"
    } catch {
        if ($JarExists) {
            Write-Host "警告: 找不到 Maven，无法重新打包，将继续使用旧 Jar 启动，最新的后端改动不会生效。" -ForegroundColor Yellow
            return
        }
        throw
    }

    Write-Host "执行: mvn -DskipTests clean package（首次或改动较多时可能需要几分钟）"
    $mvn = Start-Process -FilePath $MvnCmd -ArgumentList @("-DskipTests", "clean", "package") -WorkingDirectory $BackendDir -NoNewWindow -PassThru -Wait
    if ($mvn.ExitCode -ne 0) {
        throw "后端打包失败（mvn 退出码: $($mvn.ExitCode)）。请在 backend 目录手动执行 mvn -DskipTests clean package 查看详细错误。"
    }
    Write-Host "打包完成。" -ForegroundColor Green
}

function Test-PortInUse {
    param([int]$Port)

    $line = netstat -ano | Select-String -Pattern "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+\d+" | Select-Object -First 1
    return $null -ne $line
}

function Get-ListeningPortPids {
    param([int]$Port)

    netstat -ano |
        Select-String -Pattern "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)" |
        ForEach-Object { [int]$_.Matches[0].Groups[1].Value } |
        Sort-Object -Unique
}

function Stop-ProcessTreeById {
    param([int]$ProcessId)

    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
        return
    }

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTreeById -ProcessId ([int]$child.ProcessId)
    }

    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Stop-TrackedProcess {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return
    }

    Stop-ProcessTreeById -ProcessId $Process.Id
}

function Stop-PortsStartedByThisScript {
    if ($FrontendStarted) {
        foreach ($listenerPid in Get-ListeningPortPids 80) {
            Stop-ProcessTreeById -ProcessId $listenerPid
        }
    }

    if ($BackendStarted) {
        foreach ($listenerPid in Get-ListeningPortPids 8080) {
            Stop-ProcessTreeById -ProcessId $listenerPid
        }
    }
}

function Stop-System {
    if ($script:Stopping) {
        return
    }
    $script:Stopping = $true

    Write-Step "正在关闭前后端服务"
    Stop-TrackedProcess $script:FrontendProcess
    Stop-TrackedProcess $script:BackendProcess
    Stop-PortsStartedByThisScript
    Write-Host "已关闭。"
}

try {
    Write-Host "启动威特仓库管理系统（正式模式）" -ForegroundColor Green

    if (-not (Test-Path $BackendDir)) {
        throw "后端目录不存在: $BackendDir"
    }
    if (-not (Test-Path $FrontendDir)) {
        throw "前端目录不存在: $FrontendDir"
    }

    Import-DotEnv $EnvFile

    if (Test-PortInUse 8080) {
        throw "端口 8080 已被占用。请先关闭旧后端，或运行 system.bat stop。"
    }
    if (Test-PortInUse 80) {
        throw "端口 80 已被占用。请先关闭旧前端，或运行 system.bat stop。"
    }

    $JavaCmd = Resolve-CommandPath -Names @("java.exe", "java") -InstallHint "JDK 17"
    $PnpmCmd = Resolve-CommandPath -Names @("pnpm.cmd", "pnpm") -InstallHint "pnpm"

    Write-Step "检查后端 Jar 是否为最新"
    $JarExists = Test-Path $BackendJar
    $NeedPackage = $false
    if (-not $JarExists) {
        Write-Host "未找到 Jar，需要先打包: $BackendJar"
        $NeedPackage = $true
    } else {
        $JarTime = (Get-Item $BackendJar).LastWriteTime
        $SourceTime = Get-NewestSourceWriteTime -Path $BackendDir
        $JarStamp = $JarTime.ToString("yyyy-MM-dd HH:mm:ss")
        $SourceStamp = $SourceTime.ToString("yyyy-MM-dd HH:mm:ss")
        if ($SourceTime -gt $JarTime) {
            Write-Host "源码($SourceStamp) 比 Jar($JarStamp) 新，需要重新打包。" -ForegroundColor Yellow
            $NeedPackage = $true
        } else {
            Write-Host "Jar 已是最新（$JarStamp），跳过打包。"
        }
    }

    if ($NeedPackage) {
        Write-Step "重新打包后端"
        Invoke-BackendPackage -BackendDir $BackendDir -JarExists $JarExists
    }

    Write-Step "启动后端服务"
    if (Test-Path $BackendJar) {
        Write-Host "使用已打包 Jar: $BackendJar"
        $BackendProcess = Start-Process -FilePath $JavaCmd -ArgumentList @("-jar", $BackendJar) -WorkingDirectory $BackendDir -NoNewWindow -PassThru
    } else {
        $MvnCmd = Resolve-CommandPath -Names @("mvn.cmd", "mvn") -InstallHint "Maven"
        Write-Host "未找到 Jar，使用 Maven 启动: mvn spring-boot:run -pl ruoyi-admin-wms"
        $BackendProcess = Start-Process -FilePath $MvnCmd -ArgumentList @("spring-boot:run", "-pl", "ruoyi-admin-wms") -WorkingDirectory $BackendDir -NoNewWindow -PassThru
    }
    $BackendStarted = $true

    Write-Step "启动前端服务"
    Write-Host "使用 production 环境变量启动 Vite，本地代理 /prod-api 到 http://localhost:8080"
    $FrontendProcess = Start-Process -FilePath $PnpmCmd -ArgumentList @("run", "dev", "--", "--mode", "production", "--host", "0.0.0.0", "--port", "80") -WorkingDirectory $FrontendDir -NoNewWindow -PassThru
    $FrontendStarted = $true

    Write-Host ""
    Write-Host "系统启动中..." -ForegroundColor Green
    Write-Host "前端地址: http://localhost:80"
    Write-Host "后端地址: http://localhost:8080"
    Write-Host ""
    Write-Host "保持此窗口打开；关闭窗口或按 Ctrl+C 会自动关闭前后端。"

    while ($true) {
        Start-Sleep -Seconds 1

        if ($null -ne $BackendProcess) {
            $BackendProcess.Refresh()
            if ($BackendProcess.HasExited) {
                throw "后端进程已退出，退出码: $($BackendProcess.ExitCode)"
            }
        }

        if ($null -ne $FrontendProcess) {
            $FrontendProcess.Refresh()
            if ($FrontendProcess.HasExited) {
                throw "前端进程已退出，退出码: $($FrontendProcess.ExitCode)"
            }
        }
    }
}
catch {
    Write-Host ""
    Write-Host "启动失败: $($_.Exception.Message)" -ForegroundColor Red
    Stop-System
    exit 1
}
finally {
    Stop-System
}
