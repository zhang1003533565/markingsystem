#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$PROJECT_DIR/Frontend"
BACKEND_DIR="$PROJECT_DIR/Backend"

GIT_BRANCH="master"
JAVA_CMD="/usr/bin/java"

FRONTEND_PORT="5173"
BACKEND_PORT="8080"

FRONTEND_LOG_FILE="$FRONTEND_DIR/frontend.log"
BACKEND_LOG_FILE="$BACKEND_DIR/backend.log"
FRONTEND_PID_FILE="$FRONTEND_DIR/frontend.pid"
BACKEND_PID_FILE="$BACKEND_DIR/backend.pid"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "Missing command: $1"
    exit 1
  fi
}

stop_pid_file_process() {
  local pid_file="$1"
  if [ -f "$pid_file" ]; then
    local pid
    pid="$(cat "$pid_file" || true)"
    if [ -n "${pid:-}" ] && kill -0 "$pid" >/dev/null 2>&1; then
      log "Stopping process $pid"
      kill "$pid" || true
      sleep 2
    fi
    rm -f "$pid_file"
  fi
}

stop_frontend() {
  stop_pid_file_process "$FRONTEND_PID_FILE"
  local pids
  pids="$(pgrep -f "vite --host 0.0.0.0" || true)"
  if [ -n "$pids" ]; then
    log "Stopping old frontend process: $pids"
    pkill -f "vite --host 0.0.0.0" || true
    sleep 2
  fi

  pids="$(pgrep -f "vite preview" || true)"
  if [ -n "$pids" ]; then
    log "Stopping old frontend preview process: $pids"
    pkill -f "vite preview" || true
    sleep 2
  fi
}

stop_backend() {
  stop_pid_file_process "$BACKEND_PID_FILE"
  local pids
  pids="$(pgrep -f "org.codehaus.plexus.classworlds.launcher.Launcher spring-boot:run" || true)"
  if [ -n "$pids" ]; then
    log "Stopping old backend launcher: $pids"
    pkill -f "org.codehaus.plexus.classworlds.launcher.Launcher spring-boot:run" || true
    sleep 2
  fi

  pids="$(pgrep -f "com.markingsystem.BackendApplication" || true)"
  if [ -n "$pids" ]; then
    log "Stopping old backend app: $pids"
    pkill -f "com.markingsystem.BackendApplication" || true
    sleep 2
  fi

  pids="$(pgrep -f "Backend-1.0.0.jar" || true)"
  if [ -n "$pids" ]; then
    log "Stopping old backend jar: $pids"
    pkill -f "Backend-1.0.0.jar" || true
    sleep 2
  fi
}

start_frontend() {
  cd "$FRONTEND_DIR"
  log "Starting frontend on port $FRONTEND_PORT"
  nohup npm run preview -- --host 0.0.0.0 --port "$FRONTEND_PORT" --strictPort >"$FRONTEND_LOG_FILE" 2>&1 &
  echo $! > "$FRONTEND_PID_FILE"
  sleep 3

  if kill -0 "$(cat "$FRONTEND_PID_FILE")" >/dev/null 2>&1; then
    log "Frontend started, pid=$(cat "$FRONTEND_PID_FILE")"
  else
    log "Frontend failed to start, check log: $FRONTEND_LOG_FILE"
    exit 1
  fi
}

start_backend() {
  cd "$BACKEND_DIR"
  log "Starting backend on port $BACKEND_PORT"
  nohup "$JAVA_CMD" -jar target/Backend-1.0.0.jar >"$BACKEND_LOG_FILE" 2>&1 &
  echo $! > "$BACKEND_PID_FILE"
  sleep 5

  if kill -0 "$(cat "$BACKEND_PID_FILE")" >/dev/null 2>&1; then
    log "Backend started, pid=$(cat "$BACKEND_PID_FILE")"
  else
    log "Backend failed to start, check log: $BACKEND_LOG_FILE"
    exit 1
  fi
}

require_cmd git
require_cmd npm
require_cmd "$JAVA_CMD"
require_cmd pgrep
require_cmd pkill

log "Deploy start"
cd "$PROJECT_DIR"

log "Fetching latest code"
git fetch origin
git checkout "$GIT_BRANCH"
git pull origin "$GIT_BRANCH"

log "Installing frontend dependencies"
cd "$FRONTEND_DIR"
npm install

log "Building frontend"
npm run build

log "Building backend"
cd "$BACKEND_DIR"
chmod +x mvnw
./mvnw clean package -DskipTests

stop_frontend
stop_backend

start_frontend
start_backend

log "Deploy finished"
