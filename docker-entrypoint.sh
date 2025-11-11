#!/usr/bin/env bash
set -euo pipefail

MEDIA_DIR="${SMSARCHIVE_MEDIA_ROOT:-/app/media/messages}"
APP_JAR="/app/app.jar"
RUN_UID=10110
RUN_GID=10110
RUN_USER=appuser
RUN_GROUP=appgroup
DB_HOST="${DB_HOST:-db}"  # optional override
DB_PORT="${DB_PORT_CONTAINER:-5432}"  # internal container port (not host mapped)
MAX_WAIT_SECONDS="${DB_WAIT_MAX_SECONDS:-60}"

log() { echo "[entrypoint] $*"; }

ensure_media_dir() {
  if [ ! -d "$MEDIA_DIR" ]; then
    log "Creating media dir $MEDIA_DIR"
    mkdir -p "$MEDIA_DIR"
  fi
  # Only chown if ownership differs (avoid expensive recursive chown every start)
  current_uid=$(stat -c %u "$MEDIA_DIR") || current_uid=0
  current_gid=$(stat -c %g "$MEDIA_DIR") || current_gid=0
  if [ "$current_uid" != "$RUN_UID" ] || [ "$current_gid" != "$RUN_GID" ]; then
    log "Adjusting ownership of $MEDIA_DIR from ${current_uid}:${current_gid} to ${RUN_UID}:${RUN_GID}"
    chown -R ${RUN_USER}:${RUN_GROUP} "$MEDIA_DIR"
  fi
  # Set directory permissions (setgid bit so group stays consistent)
  find "$MEDIA_DIR" -type d -exec chmod 2775 {} + || true
  # Files: rw for owner/group, read for others (images are served)
  find "$MEDIA_DIR" -type f -exec chmod 664 {} + || true
}

wait_for_db() {
  log "Waiting for Postgres at ${DB_HOST}:${DB_PORT} (max ${MAX_WAIT_SECONDS}s)"
  local start_ts end_ts
  start_ts=$(date +%s)
  while true; do
    if nc -z -w 1 "${DB_HOST}" "${DB_PORT}" 2>/dev/null; then
      log "Postgres reachable"
      return 0
    fi
    end_ts=$(date +%s)
    if (( end_ts - start_ts >= MAX_WAIT_SECONDS )); then
      log "Timed out waiting for Postgres after ${MAX_WAIT_SECONDS}s"
      return 1
    fi
    sleep 2
  done
}

if [ "$(id -u)" = "0" ]; then
  ensure_media_dir
  wait_for_db || exit 1
  log "Dropping privileges to ${RUN_USER}:${RUN_GROUP}"
  exec gosu ${RUN_USER}:${RUN_GROUP} java ${JAVA_OPTS} -jar "${APP_JAR}"
else
  wait_for_db || exit 1
  exec java ${JAVA_OPTS} -jar "${APP_JAR}"
fi
