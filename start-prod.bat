@echo off
chcp 65001 >nul
title 威特仓库管理系统 - 正式启动

set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%start-prod.ps1"

if not exist "%PS_SCRIPT%" (
    echo 错误: 找不到启动脚本: %PS_SCRIPT%
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo 系统启动脚本异常退出，错误码: %EXIT_CODE%
    pause
)
exit /b %EXIT_CODE%
