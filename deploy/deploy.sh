#!/bin/bash
# ============================================================
# XianYuAssistant 服务器端手动部署脚本
# 用法：bash deploy.sh <版本号>
# 示例：bash deploy.sh 2.0.3
# 功能：从 GHCR 拉取指定版本镜像 → 重建容器 → 重载 Nginx
# 前置：服务器需先 docker login ghcr.io（见 README）
# ============================================================

set -e

if [ -z "$1" ]; then
  echo "用法: bash deploy.sh <版本号>"
  echo "示例: bash deploy.sh 2.0.3"
  exit 1
fi

VERSION="$1"
CONTAINER_NAME="xianyu-assistant"
DATA_DIR="/app/fish/db"
LOG_DIR="/app/fish/logs"
DOCKER_NETWORK="app-network"
NGINX_CONTAINER="nginx"
IMAGE="ghcr.io/glser/xianyu-assistant:${VERSION}"

echo "📦 确保数据目录存在..."
mkdir -p "$DATA_DIR" "$LOG_DIR"

echo "🌐 确保 Docker 网络存在..."
docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"

echo "⬇️  拉取镜像: ${IMAGE}..."
docker pull "$IMAGE"

echo "🔄 重建容器..."
docker rm -f "$CONTAINER_NAME" || true
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --network "$DOCKER_NETWORK" \
  -p 127.0.0.1:12400:12400 \
  -v "$DATA_DIR:/app/dbdata" \
  -v "$LOG_DIR:/app/logs" \
  "$IMAGE"

echo "🌐 重载 Nginx..."
docker exec "$NGINX_CONTAINER" nginx -t
docker exec "$NGINX_CONTAINER" nginx -s reload

echo "🧹 清理悬空镜像..."
docker image prune -f

echo "✅ 部署完成: ${IMAGE}"
docker ps --filter name="$CONTAINER_NAME" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
