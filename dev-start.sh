#!/bin/bash
set -u

echo "启动威特仓库管理系统 (开发模式)"

# 获取脚本目录
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 加载本地环境变量（数据库账号密码等，文件已被 .gitignore 忽略）
if [ -f "$DIR/backend/.env" ]; then
    echo "加载 backend/.env 环境变量..."
    set -a
    . "$DIR/backend/.env"
    set +a
fi

BACKEND_PID=""
FRONTEND_PID=""

kill_process_tree() {
    local pid="${1:-}"
    local signal="${2:-TERM}"
    if [ -z "$pid" ]; then
        return
    fi

    # Maven/Spring Boot 和 Vite 都可能留下子进程，先杀子进程再杀进程组。
    if command -v pkill >/dev/null 2>&1; then
        pkill "-$signal" -P "$pid" >/dev/null 2>&1 || true
    fi
    kill "-$signal" "-$pid" >/dev/null 2>&1 || true
    kill "-$signal" "$pid" >/dev/null 2>&1 || true
}

# 清理函数
cleanup() {
    echo "停止系统..."
    trap - SIGINT SIGTERM EXIT
    kill_process_tree "$FRONTEND_PID"
    kill_process_tree "$BACKEND_PID"
    sleep 1
    kill_process_tree "$FRONTEND_PID" KILL
    kill_process_tree "$BACKEND_PID" KILL
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# 开启 job control 后，后台任务会放进独立进程组，Ctrl+C 时可以整组关闭。
set -m

# 启动后端
echo "启动后端..."
(
    cd "$DIR/backend" && exec mvn spring-boot:run -pl ruoyi-admin-wms
) &
BACKEND_PID=$!

# 启动前端
echo "启动前端..."
(
    cd "$DIR/frontend-Vue" && exec npm run dev
) &
FRONTEND_PID=$!

echo "系统已启动 - 前端: http://localhost:80, 后端: http://localhost:8080"
echo "按 Ctrl+C 停止"

wait "$BACKEND_PID" "$FRONTEND_PID"
