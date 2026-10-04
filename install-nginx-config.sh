#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
NGINX_CONF_NAME="marking-system"
SOURCE_CONF="$PROJECT_DIR/nginx/${NGINX_CONF_NAME}.conf"
AVAILABLE_CONF="/etc/nginx/sites-available/${NGINX_CONF_NAME}"
ENABLED_CONF="/etc/nginx/sites-enabled/${NGINX_CONF_NAME}"

if [ ! -f "$SOURCE_CONF" ]; then
  echo "Missing nginx config: $SOURCE_CONF" >&2
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "nginx is not installed. Run: apt update && apt install -y nginx" >&2
  exit 1
fi

cp "$SOURCE_CONF" "$AVAILABLE_CONF"
ln -sfn "$AVAILABLE_CONF" "$ENABLED_CONF"
rm -f /etc/nginx/sites-enabled/default

nginx -t
systemctl reload nginx

echo "Nginx config installed for marking.lukezhang.work."
