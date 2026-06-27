$ErrorActionPreference = "Stop"

Write-Host "启动威特仓库管理系统 (开发模式)"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $ScriptDir "backend"
$FrontendDir = Join-Path $ScriptDir "frontend-Vue"
$EnvFile = Join-Path $BackendDir ".env"
$BackendProcess = $null
$FrontendProcess = $null

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    Write-Host "加载 backend/.env 环境变量..."
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

function Stop-ProcessTree {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process -or $Process.HasExited) {
        return
    }

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($Process.Id)" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        $childProcess = Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue
        if ($null -ne $childProcess) {
            Stop-ProcessTree $childProcess
        }
    }

    if (-not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    }
}

function Stop-System {
    Write-Host ""
    Write-Host "停止系统..."
    Stop-ProcessTree $FrontendProcess
    Stop-ProcessTree $BackendProcess
}

Import-DotEnv $EnvFile

try {
    Write-Host "启动后端..."
    $BackendProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run", "-pl", "ruoyi-admin-wms" -WorkingDirectory $BackendDir -NoNewWindow -PassThru

    Write-Host "启动前端..."
    $FrontendProcess = Start-Process -FilePath "pnpm" -ArgumentList "run", "dev" -WorkingDirectory $FrontendDir -NoNewWindow -PassThru

    Write-Host "系统已启动 - 前端: http://localhost:80, 后端: http://localhost:8080"
    Write-Host "按 Ctrl+C 停止"

    while (-not $BackendProcess.HasExited -and -not $FrontendProcess.HasExited) {
        Start-Sleep -Seconds 1
        $BackendProcess.Refresh()
        $FrontendProcess.Refresh()
    }
}
finally {
    Stop-System
}
