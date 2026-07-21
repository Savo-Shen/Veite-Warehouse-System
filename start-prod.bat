@echo off
chcp 65001 >nul
title 威特仓库管理系统 - 正式启动
setlocal

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%start-prod.ps1"

if not exist "%PS_SCRIPT%" (
    echo 错误: 找不到启动脚本: %PS_SCRIPT%
    pause
    exit /b 1
)

rem Explicitly decode the script as UTF-8. Windows PowerShell 5.1 otherwise
rem treats a UTF-8 file without BOM as the local ANSI code page.
set "VEITE_WMS_SCRIPT_DIR=%SCRIPT_DIR%"
set "VEITE_WMS_PS_SCRIPT=%PS_SCRIPT%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$scriptPath = $env:VEITE_WMS_PS_SCRIPT; $scriptText = [System.IO.File]::ReadAllText($scriptPath, [System.Text.Encoding]::UTF8); & ([ScriptBlock]::Create($scriptText))"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo 系统启动脚本异常退出，错误码: %EXIT_CODE%
    pause
)
exit /b %EXIT_CODE%
