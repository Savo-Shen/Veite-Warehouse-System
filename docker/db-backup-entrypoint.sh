#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USERNAME:?MYSQL_USERNAME is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${BACKUP_DIR:=/backups}"
: "${BACKUP_RETENTION_DAYS:=7}"
: "${BACKUP_HOUR:=3}"
: "${BACKUP_MINUTE:=0}"

mkdir -p "$BACKUP_DIR"

run_backup() {
  stamp=$(date '+%Y%m%d-%H%M%S')
  target="$BACKUP_DIR/wms-$stamp.sql.gz"
  temporary="$target.part"
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] 开始备份 $MYSQL_HOST/$MYSQL_DATABASE"
  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
    --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
    --single-transaction --routines --events --triggers --hex-blob \
    --default-character-set=utf8mb4 "$MYSQL_DATABASE" | gzip -c > "$temporary"
  mv "$temporary" "$target"
  find "$BACKUP_DIR" -type f -name 'wms-*.sql.gz' -mtime "+$BACKUP_RETENTION_DAYS" -delete
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] 备份完成 $target"
}

if [ "${BACKUP_RUN_ON_START:-false}" = "true" ]; then
  run_backup
fi

while true; do
  now=$(date +%s)
  target=$(date -d "today ${BACKUP_HOUR}:${BACKUP_MINUTE}:00" +%s)
  if [ "$target" -le "$now" ]; then
    target=$(date -d "tomorrow ${BACKUP_HOUR}:${BACKUP_MINUTE}:00" +%s)
  fi
  sleep_seconds=$((target - now))
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] 下次备份将在 $sleep_seconds 秒后执行"
  sleep "$sleep_seconds"
  run_backup
done
