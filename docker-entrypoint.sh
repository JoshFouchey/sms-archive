#!/usr/bin/env bash
set -euo pipefail

MEDIA_DIR="${SMSARCHIVE_MEDIA_ROOT:-/app/media/messages}"
APP_JAR="/app/app.jar"
RUN_UID=10110
RUN_GID=10110
RUN_USER=appuser
RUN_GROUP=appgroup
DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT_CONTAINER:-5432}"
MAX_WAIT_SECONDS="${DB_WAIT_MAX_SECONDS:-60}"
DB_USER_ENV="${SPRING_DATASOURCE_USERNAME:-sms_user}"

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
  command -v nc >/dev/null 2>&1 || log "nc not found (will rely on pg_isready)"
  command -v pg_isready >/dev/null 2>&1 || log "pg_isready not found (will rely on nc)"
  local start_ts=$(date +%s)
  local elapsed=0
  while (( elapsed < MAX_WAIT_SECONDS )); do
    # DNS resolution diagnostic
    if getent hosts "${DB_HOST}" >/dev/null 2>&1; then
      :
    else
      log "DNS: host '${DB_HOST}' not yet resolvable"
    fi

    # Try pg_isready first if available
    if command -v pg_isready >/dev/null 2>&1; then
      if pg_isready -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER_ENV}" >/dev/null 2>&1; then
        log "Postgres reachable (pg_isready)"
        return 0
      fi
    fi

    # Fallback: nc simple TCP check
    if command -v nc >/dev/null 2>&1; then
      if nc -z -w 1 "${DB_HOST}" "${DB_PORT}" >/dev/null 2>&1; then
        log "Postgres reachable (nc)"
        return 0
      fi
    fi

    sleep 2
    elapsed=$(( $(date +%s) - start_ts ))
  done
  log "Timed out waiting for Postgres after ${MAX_WAIT_SECONDS}s"
  return 1
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
