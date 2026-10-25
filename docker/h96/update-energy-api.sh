#!/bin/bash
# Update energy-control-api on the H96 box. Usage: ./update-energy-api.sh
# Pulls the latest image (with timeout and retries), recreates the container,
# waits for the healthcheck and logs every step to ~/update-energy-api.log.
set -euo pipefail

IMAGE="ghcr.io/iiilja/energy-control-api:latest"
NAME="energy-control-api"
LOG="$HOME/update-energy-api.log"
PULL_TIMEOUT=300     # seconds per pull attempt
PULL_ATTEMPTS=3
HEALTH_TIMEOUT=180   # seconds to wait for the container to become healthy

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" | tee -a "$LOG"; }
trap 'log "FAILED at line $LINENO"' ERR

log "=== Update started"
log "Disk free: $(df -h /var/lib/docker | awk 'NR==2 {print $4 " of " $2}')"

OLD_ID=$(docker image inspect --format '{{.Id}}' "$IMAGE" 2>/dev/null || echo none)
log "Image before pull: ${OLD_ID:7:12}"

# --- pull with timeout and retries ---
for i in $(seq 1 "$PULL_ATTEMPTS"); do
  log "Pulling $IMAGE (attempt $i/$PULL_ATTEMPTS, timeout ${PULL_TIMEOUT}s)"
  if timeout "$PULL_TIMEOUT" docker pull "$IMAGE" 2>&1 | tee -a "$LOG"; then
    log "Pull finished"
    break
  fi
  log "Pull failed or timed out"
  if [ "$i" -eq "$PULL_ATTEMPTS" ]; then
    log "Giving up, container left as it was"
    exit 1
  fi
  sleep 10
done

NEW_ID=$(docker image inspect --format '{{.Id}}' "$IMAGE")
RUNNING_ID=$(docker inspect --format '{{.Image}}' "$NAME" 2>/dev/null || echo none)
if [ "$NEW_ID" = "$RUNNING_ID" ]; then
  log "Container already runs the latest image (${NEW_ID:7:12}), nothing to do"
  exit 0
fi
log "New image: ${NEW_ID:7:12}"

# --- recreate container (docker restart would keep the old image) ---
log "Removing old container"
docker rm -f "$NAME" >/dev/null 2>&1 || true

log "Starting new container"
docker run -d \
  --name "$NAME" \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=energy_control \
  -e DB_USERNAME=energy_user \
  -e DB_PASSWORD=energy_pass \
  -e SERVER_PORT=8080 \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  -p 8080:8080 \
  --restart unless-stopped \
  "$IMAGE" | tee -a "$LOG"

# --- wait for healthcheck ---
log "Waiting for container to become healthy (up to ${HEALTH_TIMEOUT}s)"
STATUS=starting
for _ in $(seq 1 $((HEALTH_TIMEOUT / 5))); do
  STATUS=$(docker inspect --format '{{.State.Health.Status}}' "$NAME" 2>/dev/null || echo missing)
  case "$STATUS" in
    healthy)   log "Container is healthy"; break ;;
    unhealthy|missing)
               log "Container is $STATUS, last log lines:"
               docker logs --tail 50 "$NAME" 2>&1 | tee -a "$LOG"
               exit 1 ;;
  esac
  sleep 5
done
if [ "$STATUS" != "healthy" ]; then
  log "Still '$STATUS' after ${HEALTH_TIMEOUT}s, last log lines:"
  docker logs --tail 50 "$NAME" 2>&1 | tee -a "$LOG"
  exit 1
fi

log "Removing unused images"
docker image prune -f | tee -a "$LOG"
log "=== Update done"
