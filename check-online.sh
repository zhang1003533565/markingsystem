#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$PROJECT_DIR/Frontend"
BACKEND_DIR="$PROJECT_DIR/Backend"

FRONTEND_PORT="5173"
BACKEND_PORT="8080"

FRONTEND_LOG_FILE="$FRONTEND_DIR/frontend.log"
BACKEND_LOG_FILE="$BACKEND_DIR/backend.log"
FRONTEND_PID_FILE="$FRONTEND_DIR/frontend.pid"
BACKEND_PID_FILE="$BACKEND_DIR/backend.pid"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

show_pid_status() {
  local name="$1"
  local pid_file="$2"

  if [ -f "$pid_file" ]; then
    local pid
    pid="$(cat "$pid_file" || true)"
    if [ -n "${pid:-}" ] && kill -0 "$pid" >/dev/null 2>&1; then
      log "$name pid file exists, pid=$pid, status=running"
    else
      log "$name pid file exists, but process is not running"
    fi
  else
    log "$name pid file not found"
  fi
}

show_port_status() {
  local name="$1"
  local port="$2"

  if ss -lntp | grep -q ":$port "; then
    log "$name port $port is listening"
    ss -lntp | grep ":$port " || true
  else
    log "$name port $port is not listening"
  fi
}

show_recent_log() {
  local name="$1"
  local file="$2"

  if [ -f "$file" ]; then
    log "$name recent log"
    tail -n 20 "$file" || true
  else
    log "$name log file not found: $file"
  fi
}

log "Checking frontend"
show_pid_status "Frontend" "$FRONTEND_PID_FILE"
show_port_status "Frontend" "$FRONTEND_PORT"
show_recent_log "Frontend" "$FRONTEND_LOG_FILE"

log "Checking backend"
show_pid_status "Backend" "$BACKEND_PID_FILE"
show_port_status "Backend" "$BACKEND_PORT"
show_recent_log "Backend" "$BACKEND_LOG_FILE"
