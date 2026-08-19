#!/bin/bash
# ============================================================
# XianYuAssistant 服务器端手动部署脚本
# 用法：bash deploy/deploy.sh
# 功能：拉取代码 → 构建后端镜像(版本号) → 重建容器 → 重载 Nginx
# 注意：前端由 GitHub Actions 构建并上传，此脚本不处理前端
# ============================================================

set -e

PROJECT_DIR="/app/fish"
CONTAINER_NAME="xianyu-assistant"
DATA_DIR="/app/fish/db"
LOG_DIR="/app/fish/logs"
REPO_URL="https://github.com/Glser/XianYuAssistant.git"
DOCKER_NETWORK="app-network"
NGINX_CONTAINER="nginx"

echo "📦 确保数据目录存在..."
mkdir -p "$DATA_DIR" "$LOG_DIR"

echo "🌐 确保 Docker 网络存在..."
docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"

echo "📥 拉取最新代码..."
if [ ! -d "$PROJECT_DIR/.git" ]; then
  echo "首次部署，克隆代码到 $PROJECT_DIR ..."
  git clone "$REPO_URL" "$PROJECT_DIR"
fi
cd "$PROJECT_DIR"
git pull origin main

# 读取 pom.xml 项目版本号作为镜像 tag
VERSION=$(grep -A1 '<artifactId>XianYuAssistant</artifactId>' pom.xml | grep '<version>' | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
IMAGE_TAG="xianyu-assistant:${VERSION}"
echo "🏷️  镜像版本: ${IMAGE_TAG}"

echo "🏗️  构建后端镜像..."
docker build -t "$IMAGE_TAG" .

echo "🔄 重建容器（加入 $DOCKER_NETWORK 网络）..."
docker rm -f "$CONTAINER_NAME" || true
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --network "$DOCKER_NETWORK" \
  -p 127.0.0.1:12400:12400 \
  -v "$DATA_DIR:/app/dbdata" \
  -v "$LOG_DIR:/app/logs" \
  "$IMAGE_TAG"

echo "🌐 重载 Nginx（容器内）..."
docker exec "$NGINX_CONTAINER" nginx -t
docker exec "$NGINX_CONTAINER" nginx -s reload

echo "🧹 清理悬空镜像..."
docker image prune -f

echo "✅ 部署完成: ${IMAGE_TAG}"
docker ps --filter name="$CONTAINER_NAME" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
