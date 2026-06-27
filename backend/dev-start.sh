#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

if [ -f ".env" ]; then
  set -a
  # shellcheck disable=SC1091
  . ".env"
  set +a
else
  echo "backend/.env not found. Copy .env.example to .env and fill WMS_AI_API_KEY first." >&2
  exit 1
fi

mvn spring-boot:run -pl ruoyi-admin-wms
