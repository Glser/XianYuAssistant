# 自动化部署说明

## 架构

```
开发者本地 git push → GitHub Actions
                         ├─ 构建前端 (npm run build) → scp 到 /app/nginx/html/fish.oioi.lat
                         ├─ 上传 nginx 配置 → /app/nginx/conf.d
                         ├─ docker build 后端镜像 → push 到 ghcr.io/glser/xianyu-assistant:版本号
                         └─ SSH 到服务器
                              ├─ docker pull 镜像（版本号由 Actions 传入）
                              ├─ docker run 重建容器（加入 app-network）
                              └─ docker exec nginx nginx -s reload
```

- **前端**：GitHub Actions 构建，产物 scp 到服务器 `/app/nginx/html/fish.oioi.lat`（独立子目录）
- **后端镜像**：GitHub Actions 构建并推送到 GHCR，服务器只 pull 不 build
- **Nginx**：Docker 容器，在 `app-network` 网络，用容器名 `xianyu-assistant:12400` 反代后端
- **数据**：SQLite 数据库和日志在 `/app/fish/db` 和 `/app/fish/logs`，挂载到容器
- **服务器不需要 git、不需要 clone 代码**，版本号由 GitHub Actions 直接传入

### 服务器路径对照

| 宿主机路径 | 容器内路径 | 用途 |
|---|---|---|
| `/app/nginx/html/fish.oioi.lat` | `/usr/share/nginx/html/fish.oioi.lat` | 前端静态文件 |
| `/app/nginx/conf.d` | `/etc/nginx/conf.d` | Nginx 站点配置 |
| `/app/nginx/conf/nginx.conf` | `/etc/nginx/nginx.conf` | Nginx 主配置 |
| `/app/nginx/logs` | `/var/log/nginx` | Nginx 日志 |
| `/app/fish/db` | `/app/dbdata` | SQLite 数据库 |
| `/app/fish/logs` | `/app/logs` | 应用日志 |

> 服务器上不需要安装 git，也不需要 clone 项目代码。`/app/fish` 只用于存放 db 和 logs 数据目录。

---

## 首次部署（必须手动完成一次）

### 1. 准备数据目录

```bash
mkdir -p /app/fish/db /app/fish/logs /app/nginx/html/fish.oioi.lat
# 如果有旧数据库，放到 /app/fish/db/xianyu_assistant.db
```

### 2. 确保 Docker 网络存在

```bash
docker network inspect app-network >/dev/null 2>&1 || docker network create app-network
```

### 3. 服务器登录 GHCR（拉取私有镜像需要）

1. GitHub 右上角头像 → Settings → Developer settings → Personal access tokens → **Tokens (classic)** → Generate new token
2. 勾选 `read:packages` 权限，生成 token
3. 在服务器上执行：

```bash
echo "你的PAT" | docker login ghcr.io -u Glser --password-stdin
```

> 登录信息保存在 `/root/.docker/config.json`，只需执行一次。

### 4. 配置 GitHub Secrets

仓库 → Settings → Secrets and variables → Actions → **Repository secrets**：

| Secret 名称 | 值 | 说明 |
|---|---|---|
| `SERVER_HOST` | 服务器公网 IP | 例如 `123.45.67.89` |
| `SERVER_USER` | `root` | SSH 登录用户名 |
| `SERVER_SSH_KEY` | 私钥全文 | `~/.ssh/id_ed25519` 内容，含 BEGIN/END 行 |

> `GITHUB_TOKEN` 是 GitHub 自动提供的，不需要手动创建，用于推送镜像到 GHCR。

### 5. 推送代码触发首次部署

```bash
git add -A
git commit -m "feat: 自动化部署 - GHCR镜像 + nginx子目录"
git push origin main
```

到 GitHub 仓库的 **Actions** 页面查看部署进度，成功后访问 `http://fish.oioi.lat`。

---

## 日常部署

```bash
git push origin main
```

GitHub Actions 自动完成：构建前端 → 上传 → 构建镜像 → 推送 GHCR → 服务器拉取镜像重启。约 3-5 分钟。

---

## 手动部署（备选）

```bash
# 需要指定版本号
bash /path/to/deploy.sh 2.0.3
```

> 手动脚本只部署后端（从 GHCR pull 镜像），前端需 GitHub Actions 或手动构建上传。

---

## 回滚

### 代码回滚

```bash
git revert HEAD
git push origin main
```

或在 GitHub Actions 找到之前成功的 run，点 `Re-run jobs`。

### 镜像回滚

```bash
# 查看本地已有镜像
docker images ghcr.io/glser/xianyu-assistant

# 用旧版本回滚（替换 <旧版本号>）
docker rm -f xianyu-assistant
docker run -d \
  --name xianyu-assistant \
  --restart unless-stopped \
  --network app-network \
  -p 127.0.0.1:12400:12400 \
  -v /app/fish/db:/app/dbdata \
  -v /app/fish/logs:/app/logs \
  ghcr.io/glser/xianyu-assistant:<旧版本号>
docker exec nginx nginx -s reload
```

---

## 常见问题

### 502 Bad Gateway

```bash
docker inspect xianyu-assistant -f '{{json .NetworkSettings.Networks}}'
docker exec nginx wget -qO- http://xianyu-assistant:12400/api/system/version
docker logs xianyu-assistant --tail 50
```

### 前端 404 / 空白

```bash
ls -la /app/nginx/html/fish.oioi.lat/
docker exec nginx nginx -t
```

### docker pull 失败 (denied)

GHCR 登录过期，重新登录：

```bash
echo "你的PAT" | docker login ghcr.io -u Glser --password-stdin
```

### 数据丢失

只要 `/app/fish/db/xianyu_assistant.db` 还在就没丢：

```bash
ls -lh /app/fish/db/
```

---

## 文件清单

| 文件 | 作用 |
|---|---|
| `.github/workflows/deploy.yml` | GitHub Actions 自动化部署（构建镜像+前端+部署） |
| `deploy/nginx/fish.oioi.lat.conf` | Nginx 站点配置 |
| `deploy/deploy.sh` | 服务器端手动部署脚本（pull 镜像） |
| `deploy/README.md` | 本说明文档 |
| `Dockerfile` | 纯后端 Docker 镜像 |
| `vue-code/vite.config.ts` | 前端构建输出改为 `dist` |
