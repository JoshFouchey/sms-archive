#!/bin/sh
set -e
# Render nginx.conf from template allowing CSP_CONNECT_SRC customization.
TEMPLATE="/etc/nginx/templates/nginx.conf.template"
OUTPUT="/etc/nginx/conf.d/default.conf"

# Ensure variable is defined (may be empty -> only 'self' will apply)
: "${CSP_CONNECT_SRC:=}"

if [ -f "$TEMPLATE" ]; then
  echo "[frontend-entrypoint] Rendering nginx.conf with CSP_CONNECT_SRC='$CSP_CONNECT_SRC'" >&2
  envsubst '${CSP_CONNECT_SRC}' < "$TEMPLATE" > "$OUTPUT"
else
  echo "[frontend-entrypoint] Template not found, skipping render" >&2
fi

# Do not start nginx here; base image entrypoint will handle it.


