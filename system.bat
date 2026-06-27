@echo off
chcp 65001 >nul
title 威特仓库管理系统

set SCRIPT_DIR=%~dp0
set FRONTEND_DIR=%SCRIPT_DIR%frontend-Vue
set BACKEND_DIR=%SCRIPT_DIR%backend

setlocal enabledelayedexpansion

rem ---------- Functions ----------

:usage
echo 用法: %~n0 ^<start^|stop^|status^>
exit /b 1

:check_dir
if not exist "%FRONTEND_DIR%" (
    echo 错误: 前端目录不存在: %FRONTEND_DIR%
    exit /b 1
)
if not exist "%BACKEND_DIR%" (
    echo 错误: 后端目录不存在: %BACKEND_DIR%
    exit /b 1
)
exit /b 0

:check_port
rem %1=port
rem Returns 0 if port is in use, 1 if not
netstat -ano | findstr /R /C:":%1 .*LISTENING" >nul
if %errorlevel% equ 0 (
    exit /b 0
) else (
    exit /b 1
)

:get_pid_by_port
rem %1=port
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /R /C:":%1 .*LISTENING"') do (
    set PID=%%a
    goto :got_pid
)
set PID=
:got_pid
exit /b 0

:stop_process_by_pid
rem %1=pid
taskkill /PID %1% /F >nul 2>&1
exit /b 0

rem ---------- Main ----------

if "%~1"=="" (
    call :usage
)

if /I "%~1"=="start" (
    echo 正在启动威特仓库管理系统...
    call :check_dir
    if errorlevel 1 exit /b 1

    rem 检查端口占用
    call :check_port 8080
    set backend_running=%errorlevel%
    call :check_port 80
    set frontend_running=%errorlevel%

    if %backend_running% equ 0 (
        echo 后端服务已在运行，端口8080被占用。
    ) else (
        echo 启动后端服务...
        cd /d "%BACKEND_DIR%"
        start "后端服务" cmd /k "mvn spring-boot:run -pl ruoyi-admin-wms"
    )

    if %frontend_running% equ 0 (
        echo 前端服务已在运行，端口80被占用。
    ) else (
        echo 启动前端服务...
        cd /d "%FRONTEND_DIR%"
        start "前端服务" cmd /k "pnpm run dev"
    )

    echo.
    echo 系统启动完成!
    echo 前端地址: http://localhost:80
    echo 后端地址: http://localhost:8080
    echo.
    echo 关闭此窗口或按任意键退出...
    pause >nul
    exit /b 0
) else if /I "%~1"=="stop" (
    echo 正在停止威特仓库管理系统...

    rem 查找并关闭后端服务
    call :get_pid_by_port 8080
    if defined PID (
        echo 关闭后端服务，PID=%PID%...
        call :stop_process_by_pid %PID%
    ) else (
        echo 后端服务未运行。
    )

    rem 查找并关闭前端服务
    call :get_pid_by_port 80
    if defined PID (
        echo 关闭前端服务，PID=%PID%...
        call :stop_process_by_pid %PID%
    ) else (
        echo 前端服务未运行。
    )

    echo.
    echo 系统已停止。
    exit /b 0
) else if /I "%~1"=="status" (
    echo 威特仓库管理系统运行状态:
    call :check_port 8080
    if %errorlevel% equ 0 (
        echo 后端服务: 运行中 (端口8080占用)
    ) else (
        echo 后端服务: 未运行
    )

    call :check_port 80
    if %errorlevel% equ 0 (
        echo 前端服务: 运行中 (端口80占用)
    ) else (
        echo 前端服务: 未运行
    )
    exit /b 0
) else (
    call :usage
)
