#!/usr/bin/env bash
#
# 把本地 MySQL 数据库单向同步到云端数据库。
#
# 单向覆盖：本地 -> 云端。执行后云端数据库的内容会被本地数据完整替换。
# 覆盖前会先把云端现有数据导出到本地一份，作为回退用的安全备份。
#
# 连接信息从 backend/.env 读取，需要在其中配置 CLOUD_MYSQL_* 系列变量。
#
# 用法：
#   script/sync-to-cloud.sh          交互确认后执行
#   script/sync-to-cloud.sh --force  跳过确认

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$ROOT_DIR/backend/.env"

FORCE=false
[ "${1:-}" = "--force" ] && FORCE=true

step() { printf '\n==> %s\n' "$1"; }
fail() { printf 'ERROR: %s\n' "$1" >&2; exit 1; }

[ -f "$ENV_FILE" ] || fail "找不到环境变量文件: $ENV_FILE"
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

command -v mysqldump >/dev/null 2>&1 || fail "找不到 mysqldump，请先安装 MySQL 客户端"
command -v mysql >/dev/null 2>&1 || fail "找不到 mysql，请先安装 MySQL 客户端"

step "读取配置"
LOCAL_HOST="${MYSQL_HOST:-localhost}"
LOCAL_PORT="${MYSQL_PORT:-3306}"
LOCAL_DB="${MYSQL_DATABASE:?backend/.env 中缺少 MYSQL_DATABASE}"
LOCAL_USER="${MYSQL_USERNAME:?backend/.env 中缺少 MYSQL_USERNAME}"
LOCAL_PASSWORD="${MYSQL_PASSWORD:?backend/.env 中缺少 MYSQL_PASSWORD}"

CLOUD_HOST="${CLOUD_MYSQL_HOST:?backend/.env 中缺少 CLOUD_MYSQL_HOST}"
CLOUD_PORT="${CLOUD_MYSQL_PORT:-3306}"
CLOUD_DB="${CLOUD_MYSQL_DATABASE:-$LOCAL_DB}"
CLOUD_USER="${CLOUD_MYSQL_USERNAME:?backend/.env 中缺少 CLOUD_MYSQL_USERNAME}"
CLOUD_PASSWORD="${CLOUD_MYSQL_PASSWORD:?backend/.env 中缺少 CLOUD_MYSQL_PASSWORD}"
# 数据要走公网，默认强制 TLS
CLOUD_SSL_MODE="${CLOUD_MYSQL_SSL_MODE:-REQUIRED}"

if [ "$CLOUD_HOST" = "$LOCAL_HOST" ] && [ "$CLOUD_PORT" = "$LOCAL_PORT" ] && [ "$CLOUD_DB" = "$LOCAL_DB" ]; then
    fail "云端和本地指向同一个数据库，拒绝执行。请检查 backend/.env 中的 CLOUD_MYSQL_* 配置。"
fi

echo "本地（源）:   $LOCAL_USER@$LOCAL_HOST:$LOCAL_PORT/$LOCAL_DB"
echo "云端（目标）: $CLOUD_USER@$CLOUD_HOST:$CLOUD_PORT/$CLOUD_DB  TLS=$CLOUD_SSL_MODE"

table_count() {
    # $1=host $2=port $3=user $4=db $5=password $6=ssl_mode(可空)
    local ssl_arg=()
    [ -n "${6:-}" ] && ssl_arg=("--ssl-mode=$6")
    MYSQL_PWD="$5" mysql --host="$1" --port="$2" --user="$3" "${ssl_arg[@]}" \
        --batch --skip-column-names \
        --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$4'"
}

step "检查两端连接"
LOCAL_TABLES="$(table_count "$LOCAL_HOST" "$LOCAL_PORT" "$LOCAL_USER" "$LOCAL_DB" "$LOCAL_PASSWORD" "")" \
    || fail "连接本地数据库失败"
CLOUD_TABLES="$(table_count "$CLOUD_HOST" "$CLOUD_PORT" "$CLOUD_USER" "$CLOUD_DB" "$CLOUD_PASSWORD" "$CLOUD_SSL_MODE")" \
    || fail "连接云端数据库失败"
echo "本地表数量: $LOCAL_TABLES"
echo "云端表数量: $CLOUD_TABLES（将被覆盖）"

if [ "$FORCE" != true ]; then
    echo
    echo "即将用本地数据完整覆盖云端数据库 $CLOUD_DB。"
    echo "云端现有数据会先备份到本地，但覆盖本身不可撤销。"
    printf '确认请输入云端数据库名 [%s]: ' "$CLOUD_DB"
    read -r answer
    if [ "$answer" != "$CLOUD_DB" ]; then
        echo "已取消。"
        exit 0
    fi
fi

BACKUP_DIR="${WMS_BACKUP_DIR:-$ROOT_DIR/backups}"
mkdir -p "$BACKUP_DIR"
STAMP="$(date '+%Y%m%d-%H%M%S')"

DUMP_ARGS=(
    --single-transaction --routines --events --triggers --hex-blob
    --add-drop-table --no-tablespaces --set-gtid-purged=OFF
    --default-character-set=utf8mb4
)

step "备份云端现有数据（回退用）"
CLOUD_BACKUP="$BACKUP_DIR/cloud-before-sync-$STAMP.sql"
if [ "$CLOUD_TABLES" -gt 0 ]; then
    MYSQL_PWD="$CLOUD_PASSWORD" mysqldump "${DUMP_ARGS[@]}" \
        --host="$CLOUD_HOST" --port="$CLOUD_PORT" --user="$CLOUD_USER" \
        --ssl-mode="$CLOUD_SSL_MODE" "$CLOUD_DB" > "$CLOUD_BACKUP" \
        || fail "导出云端数据失败"
    echo "已保存: $CLOUD_BACKUP"
else
    echo "云端为空库，跳过。"
fi

step "导出本地数据"
LOCAL_DUMP="$BACKUP_DIR/local-sync-$STAMP.sql"
MYSQL_PWD="$LOCAL_PASSWORD" mysqldump "${DUMP_ARGS[@]}" \
    --host="$LOCAL_HOST" --port="$LOCAL_PORT" --user="$LOCAL_USER" \
    "$LOCAL_DB" > "$LOCAL_DUMP" \
    || fail "导出本地数据失败"
echo "已导出: $LOCAL_DUMP（$(du -h "$LOCAL_DUMP" | cut -f1)）"

step "写入云端"
MYSQL_PWD="$CLOUD_PASSWORD" mysql \
    --host="$CLOUD_HOST" --port="$CLOUD_PORT" --user="$CLOUD_USER" \
    --ssl-mode="$CLOUD_SSL_MODE" --default-character-set=utf8mb4 \
    "$CLOUD_DB" < "$LOCAL_DUMP" \
    || fail "写入云端失败。云端可能处于半更新状态，可用 $CLOUD_BACKUP 回退。"

step "校验"
AFTER="$(table_count "$CLOUD_HOST" "$CLOUD_PORT" "$CLOUD_USER" "$CLOUD_DB" "$CLOUD_PASSWORD" "$CLOUD_SSL_MODE")"
echo "云端表数量: $AFTER（本地为 $LOCAL_TABLES）"
if [ "$AFTER" != "$LOCAL_TABLES" ]; then
    echo "表数量不一致，请检查上面的输出。"
else
    echo "同步完成。"
fi

rm -f "$LOCAL_DUMP"
