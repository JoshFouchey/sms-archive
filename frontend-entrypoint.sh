#!/bin/sh
set -e
# Render nginx.conf from template allowing CSP_CONNECT_SRC customization.
# This runs AFTER nginx's built-in 20-envsubst script (which creates nginx.conf).
# We render our own version to default.conf and clean up duplicates.

TEMPLATE="/etc/nginx/templates/nginx.conf.template"
OUTPUT="/etc/nginx/conf.d/default.conf"

: "${CSP_CONNECT_SRC:=}"

if [ -f "$TEMPLATE" ]; then
  echo "[frontend-entrypoint] Rendering nginx.conf with CSP_CONNECT_SRC='$CSP_CONNECT_SRC'" >&2
  envsubst '${CSP_CONNECT_SRC}' < "$TEMPLATE" > "$OUTPUT"
fi

# Remove duplicate configs to avoid "conflicting server name" warnings
rm -f /etc/nginx/conf.d/nginx.conf /etc/nginx/conf.d/legacy.conf


