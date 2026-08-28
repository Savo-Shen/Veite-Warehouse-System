#!/usr/bin/env bash
# 构建并部署到 savo-prod（wms.savo-shen.com）。
#
# 部署形态：本机构建 -> scp 到 savo-prod:~/staging/ -> 服务器上 ~/deploy-wms.sh 落位。
# 服务器上没有 Maven / Node，构建只能在本机做。
#
#   ./script/deploy-to-savo-prod.sh            # 构建 + 上传 + 重启
#   ./script/deploy-to-savo-prod.sh --stage    # 只构建 + 上传，不重启（自己挑时间重启）
#   ./script/deploy-to-savo-prod.sh --rollback # 回滚到上一次部署前的备份
set -euo pipefail

HOST=savo-prod
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/backend/ruoyi-admin-wms/target/ruoyi-admin-wms.jar"
DIST="$ROOT/frontend-Vue/dist"
MODE="${1:-deploy}"

step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }

if [ "$MODE" = "--rollback" ]; then
    step "回滚到上一次部署前的备份"
    ssh "$HOST" 'set -e
        LATEST=$(ls -1d ~/wms-backup-* 2>/dev/null | sort | tail -1)
        [ -n "$LATEST" ] || { echo "没有找到备份目录 ~/wms-backup-*"; exit 1; }
        echo "使用备份: $LATEST"
        sudo cp "$LATEST/app.jar" /opt/wms/app.jar
        sudo rm -rf /opt/wms/dist && sudo cp -r "$LATEST/dist" /opt/wms/dist
        sudo chown -R wms:wms /opt/wms/app.jar /opt/wms/dist
        sudo chmod -R a+rX /opt/wms/dist
        sudo systemctl restart wms-backend'
    echo "已回滚。"
    exit 0
fi

step "锁定 JDK 17"
# 项目 Lombok 1.18.30 在 JDK 21+ 上编译会报 "TypeTag :: UNKNOWN"，
# 必须显式指到 17，不能依赖系统默认 java。
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17'; then
    if [ -x /usr/libexec/java_home ]; then
        JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null)" || {
            echo "找不到 JDK 17。装一个：brew install --cask temurin@17"; exit 1; }
    else
        echo "请把 JAVA_HOME 指向 JDK 17 后重试"; exit 1
    fi
fi
export JAVA_HOME
echo "JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1

step "构建后端（必须带 -Pprod）"
# -Pprod 不能省：Maven 默认激活 dev profile，打出来的包 actuator 全量暴露、
# p6spy 打开、代码生成器也会被打进去。
( cd "$ROOT/backend" && mvn -Pprod -DskipTests clean package )

step "构建前端"
( cd "$ROOT/frontend-Vue" && pnpm run build:prod )

step "校验产物确实是 prod 包"
unzip -p "$JAR" BOOT-INF/classes/application.yml | grep -qE '^\s*active:\s*prod\s*$' \
    || { echo "产物不是 prod 包，中止"; exit 1; }
if unzip -l "$JAR" | grep -q 'ruoyi-generator'; then
    echo "产物里还有 ruoyi-generator，中止（应只在 dev profile 引入）"; exit 1
fi
echo "OK：active=prod，且不含 ruoyi-generator"

step "备份线上现有产物"
STAMP=$(date +%Y%m%d-%H%M%S)
ssh "$HOST" "set -e
    mkdir -p ~/wms-backup-$STAMP
    sudo cp /opt/wms/app.jar ~/wms-backup-$STAMP/app.jar
    sudo cp -r /opt/wms/dist ~/wms-backup-$STAMP/dist
    sudo chown -R \$(id -un):\$(id -gn) ~/wms-backup-$STAMP
    echo '备份于 ~/wms-backup-$STAMP'"

step "上传到 ~/staging"
ssh "$HOST" 'mkdir -p ~/staging && rm -rf ~/staging/dist'
scp -q "$JAR" "$HOST:~/staging/ruoyi-admin-wms.jar"
scp -qr "$DIST" "$HOST:~/staging/dist"
echo "上传完成"

if [ "$MODE" = "--stage" ]; then
    echo
    echo "已上传但未部署。到服务器上执行 ~/deploy-wms.sh 落位并重启。"
    exit 0
fi

step "落位并重启"
ssh "$HOST" 'set -e
    sudo cp ~/staging/ruoyi-admin-wms.jar /opt/wms/app.jar
    sudo rm -rf /opt/wms/dist && sudo cp -r ~/staging/dist /opt/wms/dist
    sudo chown -R wms:wms /opt/wms/app.jar /opt/wms/dist
    sudo chmod -R a+rX /opt/wms/dist
    sudo systemctl restart wms-backend'

step "等待就绪"
# actuator 在 nginx 上是 deny 的，健康检查只能从服务器本机打 8080
for i in $(seq 1 30); do
    if ssh "$HOST" 'curl -sf --max-time 3 http://127.0.0.1:8080/actuator/health' 2>/dev/null | grep -q '"status":"UP"'; then
        echo "后端已就绪"; break
    fi
    [ "$i" = 30 ] && { echo "健康检查超时，看日志：ssh $HOST 'sudo tail -50 /opt/wms/logs/stderr.log'"; exit 1; }
    sleep 3
done

step "外部冒烟"
printf '  首页          -> %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 https://wms.savo-shen.com/)"
printf '  /captchaImage -> %s\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 https://wms.savo-shen.com/prod-api/captchaImage)"
printf '  /v3/api-docs  -> %s (应为 403)\n' "$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 https://wms.savo-shen.com/prod-api/v3/api-docs)"

step "完成"
echo "回滚：./script/deploy-to-savo-prod.sh --rollback"
