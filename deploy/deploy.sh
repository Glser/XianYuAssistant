#!/bin/bash
# ============================================================
# XianYuAssistant 服务器端手动部署脚本
# 用法：ACR_NAMESPACE=<命名空间> bash deploy.sh <版本号>
# 示例：ACR_NAMESPACE=glser bash deploy.sh 2.0.3
# 功能：从北京 ACR 拉取指定镜像 → 健康检查 → 重建容器 → 重载 Nginx
# 前置：服务器需先 docker login registry.cn-beijing.aliyuncs.com（见 README）
# ============================================================

set -e

if [ -z "$1" ]; then
  echo "用法: ACR_NAMESPACE=<命名空间> bash deploy.sh <版本号>"
  echo "示例: ACR_NAMESPACE=glser bash deploy.sh 2.0.3"
  exit 1
fi

if [ -z "${ACR_NAMESPACE:-}" ]; then
  echo "请设置 ACR_NAMESPACE，例如：ACR_NAMESPACE=glser bash deploy.sh <版本号>"
  exit 1
fi

VERSION="$1"
ACR_REGISTRY="${ACR_REGISTRY:-registry.cn-beijing.aliyuncs.com}"
CONTAINER_NAME="xianyu-assistant"
DATA_DIR="/app/fish/db"
LOG_DIR="/app/fish/logs"
DOCKER_NETWORK="app-network"
NGINX_CONTAINER="nginx"
ACR_IMAGE="${ACR_REGISTRY}/${ACR_NAMESPACE}/xianyu-assistant:${VERSION}"
LOCAL_IMAGE="fish:${VERSION}"

echo "📦 确保数据目录存在..."
mkdir -p "$DATA_DIR" "$LOG_DIR"

echo "🌐 确保 Docker 网络存在..."
docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"

echo "⬇️  拉取镜像: ${ACR_IMAGE}..."
PULL_SUCCESS=0
for attempt in 1 2 3; do
  echo "第 ${attempt}/3 次拉取镜像"
  if docker pull "$ACR_IMAGE"; then
    PULL_SUCCESS=1
    break
  fi
  sleep 20
done

if [ "$PULL_SUCCESS" -ne 1 ]; then
  echo "镜像拉取连续失败 3 次。"
  exit 1
fi

echo "🏷️  标记本地镜像: ${LOCAL_IMAGE}..."
docker tag "$ACR_IMAGE" "$LOCAL_IMAGE"

OLD_IMAGE="$(docker inspect "$CONTAINER_NAME" --format '{{.Config.Image}}' 2>/dev/null || true)"

run_app() {
  docker run -d \
    --name "$CONTAINER_NAME" \
    --restart unless-stopped \
    --network "$DOCKER_NETWORK" \
    -p 127.0.0.1:12400:12400 \
    -v "$DATA_DIR:/app/dbdata" \
    -v "$LOG_DIR:/app/logs" \
    "$1"
}

wait_for_health() {
  for attempt in {1..30}; do
    STATUS="$(docker inspect "$CONTAINER_NAME" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' 2>/dev/null || true)"
    case "$STATUS" in
      healthy) return 0 ;;
      unhealthy|exited|dead)
        docker logs "$CONTAINER_NAME" --tail 100 || true
        return 1
        ;;
    esac
    sleep 5
  done
  docker logs "$CONTAINER_NAME" --tail 100 || true
  return 1
}

rollback() {
  echo "新容器启动失败，尝试恢复上一版本。"
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  if [ -n "$OLD_IMAGE" ]; then
    run_app "$OLD_IMAGE" || true
  fi
  exit 1
}

echo "⏹️  停止旧版本容器..."
docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker rm "$CONTAINER_NAME" >/dev/null 2>&1 || true

echo "🚀 启动新版本: ${LOCAL_IMAGE}..."
run_app "$LOCAL_IMAGE" || rollback
wait_for_health || rollback

echo "🌐 重载 Nginx..."
docker exec "$NGINX_CONTAINER" nginx -t || rollback
docker exec "$NGINX_CONTAINER" nginx -s reload || rollback

echo "🧹 清理悬空镜像..."
docker image prune -f

echo "✅ 部署完成: ${LOCAL_IMAGE}"
docker ps --filter name="$CONTAINER_NAME" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
