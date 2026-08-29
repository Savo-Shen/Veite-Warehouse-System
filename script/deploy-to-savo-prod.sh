#!/usr/bin/env bash
# 构建并部署到 savo-prod（wms.savo-shen.com）。
#
# 部署形态：本机构建 -> 把胖 jar 解包成目录 -> rsync 增量同步到 savo-prod:~/staging/
#           -> 硬链接落位成 /opt/wms/releases/<时间戳>/ -> 切 /opt/wms/current -> 重启。
#
# 为什么不再 scp 整包：胖 jar 115MB，6M 上行光传就要 3 分钟。解包成目录后
# rsync 只传真正变了的文件（自有模块 jar + 自己的 class，约 2MB），几秒钟就完；
# 第三方依赖 jar 内容和时间戳都不变，一个字节都不会重传。
#
# 为什么备份不再是整份拷贝：~/staging 是 rsync 的增量基线，每个 release 都用
# 硬链接（cp -al）指向同一批 inode，所以留 5 个版本几乎不占额外磁盘。
# 回滚 = 切软链接 + 重启，不需要重新上传。
#
#   ./script/deploy-to-savo-prod.sh                 # 全量构建 + 部署
#   ./script/deploy-to-savo-prod.sh --backend-only  # 只重建后端（前端沿用服务器上现有产物）
#   ./script/deploy-to-savo-prod.sh --frontend-only # 只重建前端
#   ./script/deploy-to-savo-prod.sh --stage         # 只同步到 ~/staging，不落位不重启
#   ./script/deploy-to-savo-prod.sh --rollback      # 切回上一个 release
#   ./script/deploy-to-savo-prod.sh --list          # 看服务器上有哪些 release
#   ./script/deploy-to-savo-prod.sh --use <时间戳>   # 切到指定 release（--list 里的名字）
set -euo pipefail

HOST=savo-prod
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/backend/ruoyi-admin-wms/target/ruoyi-admin-wms.jar"
EXPLODED="$ROOT/backend/ruoyi-admin-wms/target/exploded"
DIST="$ROOT/frontend-Vue/dist"
KEEP=5   # 服务器上保留几个 release
# macOS 自带的 /usr/bin/rsync 是 openrsync，不认 --info；优先用 Homebrew 那个。
RSYNC=$( [ -x /opt/homebrew/bin/rsync ] && echo /opt/homebrew/bin/rsync || command -v rsync )
# -c 按内容校验而不是按时间戳：每次构建都会刷新所有 class 和资源的 mtime，
#    不加 -c 的话光 ip2region.xdb 那 11MB 就会每次重传一遍。
# --chmod 固定权限，避免下次同步时 rsync 又把服务器上 chmod 过的位改回去。
RSYNC_OPTS=(-a -c --delete --chmod=D755,F644 --info=stats2)

MODE="${1:-deploy}"
TARGET="${2:-}"
DO_BACKEND=1
DO_FRONTEND=1
case "$MODE" in
    deploy|--stage) ;;
    --backend-only)  DO_FRONTEND=0 ;;
    --frontend-only) DO_BACKEND=0 ;;
    --rollback|--list) ;;
    --use) [ -n "$TARGET" ] || { echo "--use 后面要跟版本时间戳，先用 --list 看有哪些"; exit 1; } ;;
    *) echo "未知参数：$MODE（看脚本头部注释）"; exit 1 ;;
esac

step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }

# ---------------------------------------------------------------- --list
if [ "$MODE" = "--list" ]; then
    ssh "$HOST" 'CUR=$(readlink -f /opt/wms/current 2>/dev/null || echo none)
        for d in $(ls -1d /opt/wms/releases/*/ 2>/dev/null | sed "s:/$::" | sort); do
            mark="  "; [ "$d" = "$CUR" ] && mark="* "
            printf "%s%s  %s\n" "$mark" "$(basename "$d")" "$(du -sh --apparent-size "$d" | cut -f1)"
        done
        echo
        echo "* = 当前生效；磁盘实际占用（硬链接去重后）：$(du -sh /opt/wms/releases | cut -f1)"'
    exit 0
fi

# ------------------------------------------------------------------ --use
if [ "$MODE" = "--use" ]; then
    step "切到 $TARGET"
    ssh "$HOST" bash -s -- "$TARGET" <<'REMOTE'
set -e
REL=/opt/wms/releases/$1
[ -d "$REL/app" ] || { echo "没有这个 release：$1"; exit 1; }
sudo ln -sfn "$REL" /opt/wms/current.tmp
sudo mv -T /opt/wms/current.tmp /opt/wms/current
sudo systemctl restart wms-backend
echo "已切到 $1"
REMOTE
    exit 0
fi

# ------------------------------------------------------------- --rollback
if [ "$MODE" = "--rollback" ]; then
    step "切回上一个 release"
    ssh "$HOST" bash -s <<'REMOTE'
set -e
CUR=$(readlink -f /opt/wms/current)
PREV=$(ls -1d /opt/wms/releases/*/ | sed 's:/$::' | sort \
    | awk -v c="$CUR" '{a[NR]=$0} $0==c{i=NR} END{if (i>1) print a[i-1]}')
[ -n "$PREV" ] || { echo "没有更早的 release 可回滚（当前：$(basename "$CUR")）"; exit 1; }
echo "从 $(basename "$CUR") 回到 $(basename "$PREV")"
sudo ln -sfn "$PREV" /opt/wms/current.tmp
sudo mv -T /opt/wms/current.tmp /opt/wms/current
sudo systemctl restart wms-backend
REMOTE
    echo "已回滚。用 --list 确认，用 --rollback 可继续往前回。"
    exit 0
fi

# ------------------------------------------------------------------ 构建
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

if [ "$DO_BACKEND" = 1 ]; then
    step "构建后端（必须带 -Pprod）"
    # -Pprod 不能省：Maven 默认激活 dev profile，打出来的包 actuator 全量暴露、
    # p6spy 打开、代码生成器也会被打进去。
    ( cd "$ROOT/backend" && mvn -Pprod -DskipTests clean package )

    step "校验产物确实是 prod 包"
    unzip -p "$JAR" BOOT-INF/classes/application.yml | grep -qE '^\s*active:\s*prod\s*$' \
        || { echo "产物不是 prod 包，中止"; exit 1; }
    if unzip -l "$JAR" | grep -q 'ruoyi-generator'; then
        echo "产物里还有 ruoyi-generator，中止（应只在 dev profile 引入）"; exit 1
    fi
    echo "OK：active=prod，且不含 ruoyi-generator"

    step "解包成目录"
    # 解包后用 org.springframework.boot.loader.launch.JarLauncher 启动，
    # 效果和 java -jar 完全一样，但 rsync 能按文件算增量。
    rm -rf "$EXPLODED"
    mkdir -p "$EXPLODED"
    ( cd "$EXPLODED" && unzip -q "$JAR" )
    [ -d "$EXPLODED/BOOT-INF/classes" ] || { echo "解包结果不对，中止"; exit 1; }
    echo "$EXPLODED（$(du -sh "$EXPLODED" | cut -f1)）"
else
    [ -d "$EXPLODED/BOOT-INF/classes" ] || echo "（跳过后端构建，服务器沿用现有 ~/staging/app）"
fi

if [ "$DO_FRONTEND" = 1 ]; then
    step "构建前端"
    ( cd "$ROOT/frontend-Vue" && pnpm run build:prod )
fi

# ------------------------------------------------------------------ 同步
step "增量同步到 ~/staging"
ssh "$HOST" 'mkdir -p ~/staging/app ~/staging/dist'
if [ "$DO_BACKEND" = 1 ]; then
    "$RSYNC" "${RSYNC_OPTS[@]}" "$EXPLODED/" "$HOST:staging/app/" \
        | grep -E 'Number of regular files transferred|Total transferred file size' || true
fi
if [ "$DO_FRONTEND" = 1 ]; then
    "$RSYNC" "${RSYNC_OPTS[@]}" "$DIST/" "$HOST:staging/dist/" \
        | grep -E 'Number of regular files transferred|Total transferred file size' || true
fi

if [ "$MODE" = "--stage" ]; then
    echo
    echo "已同步到 ~/staging 但未落位。再跑一次不带 --stage 即可发布。"
    exit 0
fi

# ------------------------------------------------------------------ 落位
step "落位并重启"
STAMP=$(date +%Y%m%d-%H%M%S)
# cp -al 是硬链接拷贝：不复制数据，所以新 release 只为「这次真正变了的文件」付磁盘，
# 老 release 因此可以当备份长期留着。
ssh "$HOST" bash -s -- "$STAMP" "$KEEP" <<'REMOTE'
set -e
STAMP=$1
KEEP=$2
REL=/opt/wms/releases/$STAMP
sudo mkdir -p /opt/wms/releases
sudo rm -rf "$REL"
sudo mkdir -p "$REL"
sudo cp -al ~/staging/app  "$REL/app"
sudo cp -al ~/staging/dist "$REL/dist"
sudo chmod -R a+rX "$REL"
# 先建临时软链接再 mv -T，切换是原子的，不会出现半秒钟指向不存在的目录
sudo ln -sfn "$REL" /opt/wms/current.tmp
sudo mv -T /opt/wms/current.tmp /opt/wms/current
sudo systemctl restart wms-backend
echo "已切到 $STAMP"

# 保留最近 $KEEP 个，且绝不删 current 指向的那个
CUR=$(readlink -f /opt/wms/current)
ls -1d /opt/wms/releases/*/ | sed 's:/$::' | sort | head -n "-$KEEP" | while read -r d; do
    [ "$d" = "$CUR" ] && continue
    sudo rm -rf "$d"
    echo "清理旧 release: $(basename "$d")"
done
REMOTE

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
echo "回滚：./script/deploy-to-savo-prod.sh --rollback    查看版本：--list"
