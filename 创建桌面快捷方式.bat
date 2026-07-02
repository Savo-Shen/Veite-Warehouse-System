@echo off
chcp 65001 >nul
set "SCRIPT_DIR=%~dp0"
set "TARGET=%SCRIPT_DIR%start-prod.bat"
set "SHORTCUT=%USERPROFILE%\Desktop\威特仓库管理系统.lnk"

if not exist "%TARGET%" (
    echo 错误: 找不到正式启动脚本: %TARGET%
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('%SHORTCUT%'); $s.TargetPath='%TARGET%'; $s.WorkingDirectory='%SCRIPT_DIR%'; $s.IconLocation='%SystemRoot%\System32\shell32.dll,220'; $s.Description='双击启动威特仓库管理系统，关闭窗口自动停止前后端'; $s.Save()"

if errorlevel 1 (
    echo 创建快捷方式失败。
    pause
    exit /b 1
)

echo 已创建桌面快捷方式: %SHORTCUT%
pause
