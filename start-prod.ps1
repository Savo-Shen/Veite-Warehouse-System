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

function Test-JarIsProdBuild {
    param([string]$JarPath)

    # 打包 profile 是在编译期烘进 application.yml 的（@profiles.active@ 占位符）。
    # 只比对文件时间戳发现不了「上一次是用 dev profile 打的」，必须真的翻开包看一眼，
    # 否则升级后第一次启动会继续沿用旧的 dev 包，本次加固全部落空。
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
        $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
        try {
            $entry = $zip.GetEntry("BOOT-INF/classes/application.yml")
            if ($null -eq $entry) {
                return $false
            }
            $reader = New-Object System.IO.StreamReader($entry.Open())
            try {
                $content = $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
            return $content -match "(?m)^\s*active:\s*prod\s*$"
        } finally {
            $zip.Dispose()
        }
    } catch {
        Write-Host "无法读取现有 Jar 的打包 profile（$($_.Exception.Message)），保险起见重新打包。" -ForegroundColor Yellow
        return $false
    }
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

    # -Pprod 不能省：Maven 默认激活的是 dev profile，打出来的包会带上
    # dev 的配置（actuator 全量暴露、p6spy 打开、代码生成器打进包里）。
    Write-Host "执行: mvn -Pprod -DskipTests clean package（首次或改动较多时可能需要几分钟）"
    $mvn = Start-Process -FilePath $MvnCmd -ArgumentList @("-Pprod", "-DskipTests", "clean", "package") -WorkingDirectory $BackendDir -NoNewWindow -PassThru -Wait
    if ($mvn.ExitCode -ne 0) {
        throw "后端打包失败（mvn 退出码: $($mvn.ExitCode)）。请在 backend 目录手动执行 mvn -Pprod -DskipTests clean package 查看详细错误。"
    }
    Write-Host "打包完成。" -ForegroundColor Green
}

function Get-NewestFrontendSourceWriteTime {
    param([string]$Path)

    $sourceExtensions = @(".vue", ".js", ".ts", ".jsx", ".tsx", ".json", ".css", ".scss", ".html", ".svg")
    $skipDirectories = @("dist", "node_modules", ".git", ".idea", ".pnpm-store")

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

function Invoke-FrontendBuild {
    param(
        [string]$FrontendDir,
        [string]$PnpmCmd
    )

    $DistIndex = Join-Path $FrontendDir "dist\index.html"
    $NeedBuild = $false

    if (-not (Test-Path $DistIndex)) {
        Write-Host "未找到前端产物 dist\index.html，需要先构建。"
        $NeedBuild = $true
    } else {
        $DistTime = (Get-Item $DistIndex).LastWriteTime
        $SourceTime = Get-NewestFrontendSourceWriteTime -Path $FrontendDir
        if ($SourceTime -gt $DistTime) {
            Write-Host ("前端源码({0}) 比产物({1}) 新，需要重新构建。" -f $SourceTime.ToString("yyyy-MM-dd HH:mm:ss"), $DistTime.ToString("yyyy-MM-dd HH:mm:ss")) -ForegroundColor Yellow
            $NeedBuild = $true
        } else {
            Write-Host ("前端产物已是最新（{0}），跳过构建。" -f $DistTime.ToString("yyyy-MM-dd HH:mm:ss"))
        }
    }

    if (-not $NeedBuild) {
        return
    }

    if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
        Write-Host "安装前端依赖: pnpm install --frozen-lockfile"
        $install = Start-Process -FilePath $PnpmCmd -ArgumentList @("install", "--frozen-lockfile") -WorkingDirectory $FrontendDir -NoNewWindow -PassThru -Wait
        if ($install.ExitCode -ne 0) {
            throw "前端依赖安装失败（pnpm 退出码: $($install.ExitCode)）。"
        }
    }

    Write-Host "执行: pnpm run build:prod"
    $build = Start-Process -FilePath $PnpmCmd -ArgumentList @("run", "build:prod") -WorkingDirectory $FrontendDir -NoNewWindow -PassThru -Wait
    if ($build.ExitCode -ne 0) {
        throw "前端构建失败（pnpm 退出码: $($build.ExitCode)）。请在 frontend-Vue 目录手动执行 pnpm run build:prod 查看详细错误。"
    }
    Write-Host "前端构建完成。" -ForegroundColor Green
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
        } elseif (-not (Test-JarIsProdBuild -JarPath $BackendJar)) {
            Write-Host "现有 Jar 不是 prod 包（可能是历史上用默认 dev profile 打的），需要重新打包。" -ForegroundColor Yellow
            $NeedPackage = $true
        } else {
            Write-Host "Jar 已是最新的 prod 包（$JarStamp），跳过打包。"
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

    Write-Step "检查前端产物是否为最新"
    Invoke-FrontendBuild -FrontendDir $FrontendDir -PnpmCmd $PnpmCmd

    Write-Step "启动前端服务"
    # 用 vite preview 托管 dist 静态产物，而不是 vite dev。
    # vite dev 会把整个源码树通过 /@fs/ 暴露出去（能读到 backend/.env、
    # application-local.yml 里的数据库密码和 JWT 密钥），绝不能对公网开。
    # preview 只发 dist 目录，同时沿用 vite.config.js 里的 /prod-api 反代。
    Write-Host "使用 vite preview 托管 dist 静态产物，本地代理 /prod-api 到 http://localhost:8080"
    $FrontendProcess = Start-Process -FilePath $PnpmCmd -ArgumentList @("run", "preview", "--", "--mode", "production", "--host", "0.0.0.0", "--port", "80") -WorkingDirectory $FrontendDir -NoNewWindow -PassThru
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
