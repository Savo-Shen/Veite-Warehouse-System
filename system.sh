#!/bin/bash

# 威特仓库管理系统启动脚本

usage() {
    echo "用法: $0 [start|stop|status]"
    exit 1
}

if [ -z "$1" ]; then
    usage
fi

case "$1" in
    start)
        echo "正在启动威特仓库管理系统..."

        # 获取脚本所在目录
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

        # 定义日志目录
        LOG_DIR="$SCRIPT_DIR/logs"
        mkdir -p "$LOG_DIR"

        # 定义前端和后端目录
        FRONTEND_DIR="$SCRIPT_DIR/frontend-Vue"
        BACKEND_DIR="$SCRIPT_DIR/backend"

        # 检查目录是否存在
        if [ ! -d "$FRONTEND_DIR" ]; then
            echo "错误: 前端目录不存在: $FRONTEND_DIR"
            exit 1
        fi

        if [ ! -d "$BACKEND_DIR" ]; then
            echo "错误: 后端目录不存在: $BACKEND_DIR"
            exit 1
        fi

        # 检查端口是否被占用
        if lsof -i :8080 >/dev/null 2>&1 || lsof -i :80 >/dev/null 2>&1; then
            echo "系统已在运行，无需重复启动"
            exit 0
        fi

        # 启动后端
        echo "启动后端服务..."
        cd "$BACKEND_DIR"
        nohup mvn spring-boot:run -pl ruoyi-admin-wms > "$LOG_DIR/backend.log" 2>&1 &
        BACKEND_PID=$!
        echo "后端日志路径: $LOG_DIR/backend.log"

        # 等待后端启动
        sleep 10

        # 启动前端
        echo "启动前端服务..."
        cd "$FRONTEND_DIR"
        nohup pnpm run dev > "$LOG_DIR/frontend.log" 2>&1 &
        FRONTEND_PID=$!
        echo "前端日志路径: $LOG_DIR/frontend.log"

        echo "系统后台运行中，可通过日志查看状态"
        echo "停止命令：pkill -f 'spring-boot:run' && pkill -f 'pnpm run dev'"
        ;;
    stop)
        pkill -f 'spring-boot:run'
        pkill -f 'pnpm run dev'
        echo "系统已停止"
        ;;
    status)
        STATUS=0
        if lsof -i :8080 >/dev/null 2>&1; then
            echo "端口 8080 被占用，后端系统正在运行"
            lsof -i :8080
            STATUS=1
        fi
        if lsof -i :80 >/dev/null 2>&1; then
            echo "端口 80 被占用，前端系统正在运行"
            lsof -i :80
            STATUS=1
        fi
        if [ $STATUS -eq 0 ]; then
            echo "系统未运行"
        fi
        ;;
    *)
        usage
        ;;
esac
