#!/bin/bash

echo "启动威特仓库管理系统 (开发模式)"

# 获取脚本目录
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 清理函数
cleanup() {
    echo "停止系统..."
    jobs -p | xargs -r kill
    exit 0
}
trap cleanup SIGINT SIGTERM

# 启动后端
echo "启动后端..."
cd "$DIR/backend" && mvn spring-boot:run -pl ruoyi-admin-wms &

# 启动前端
echo "启动前端..."
cd "$DIR/frontend-Vue" && npm run dev &

echo "系统已启动 - 前端: http://localhost:80, 后端: http://localhost:8080"
echo "按 Ctrl+C 停止"

wait
