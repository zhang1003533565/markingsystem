#!/usr/bin/env bash

set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-marking}"
DB_USER="${DB_USER:-root}"

ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD_HASH="${ADMIN_PASSWORD_HASH:-\$2a\$10\$Tkx3wHbawir8C8vJOX0vleGTcnFTAbEXSX3X/YObAdvXrTM9OXgIq}"
ADMIN_NICKNAME="${ADMIN_NICKNAME:-系统管理员}"

mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  --password \
  --default-character-set=utf8mb4 \
  "$DB_NAME" <<SQL
INSERT IGNORE INTO roles (name, description, created_at, updated_at)
VALUES ('ADMIN', '管理员', NOW(), NOW());

INSERT INTO users (
  username,
  password,
  nickname,
  role_id,
  password_reset_required,
  created_at,
  updated_at
)
SELECT
  '${ADMIN_USERNAME}',
  '${ADMIN_PASSWORD_HASH}',
  '${ADMIN_NICKNAME}',
  r.id,
  FALSE,
  NOW(),
  NOW()
FROM roles r
WHERE r.name = 'ADMIN'
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  nickname = VALUES(nickname),
  role_id = VALUES(role_id),
  password_reset_required = FALSE,
  updated_at = NOW();
SQL

echo "Admin user '${ADMIN_USERNAME}' is ready."
