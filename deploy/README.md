# 自动化部署说明

## 架构

```
开发者本地 git push → GitHub Actions
                         ├─ 构建前端 (npm run build) → scp 到 /app/nginx/html
                         ├─ 上传 nginx 配置 → /app/nginx/conf.d
                         └─ SSH 到服务器
                              ├─ git pull 最新代码 (/app/fish)
                              ├─ docker build 后端镜像
                              ├─ docker run 重建容器（加入 app-network）
                              └─ docker exec nginx nginx -s reload
```

- **前端**：GitHub Actions 构建，产物 scp 到服务器 `/app/nginx/html`（挂载到 nginx 容器的 `/usr/share/nginx/html`）
- **后端**：服务器上 docker build + docker run，加入 `app-network` 网络，容器名 `xianyu-assistant`
- **Nginx**：Docker 容器，同在 `app-network`，用容器名 `xianyu-assistant:12400` 反代后端
- **数据**：SQLite 数据库和日志放在项目目录 `/app/fish/db` 和 `/app/fish/logs`，挂载到容器

### 服务器路径对照

| 宿主机路径 | 容器内路径 | 用途 |
|---|---|---|
| `/app/nginx/html` | `/usr/share/nginx/html` | 前端静态文件 |
| `/app/nginx/conf.d` | `/etc/nginx/conf.d` | Nginx 站点配置 |
| `/app/nginx/conf/nginx.conf` | `/etc/nginx/nginx.conf` | Nginx 主配置 |
| `/app/nginx/logs` | `/var/log/nginx` | Nginx 日志 |
| `/app/fish/db` | `/app/dbdata` | SQLite 数据库 |
| `/app/fish/logs` | `/app/logs` | 应用日志 |
| `/app/fish` | - | 项目代码（git clone） |

---

## 首次部署（必须手动完成一次）

### 1. 准备数据目录

数据库你已经备份到 `/app/fish/db/xianyu_assistant.db`，确保日志目录也存在：

```bash
mkdir -p /app/fish/db /app/fish/logs
ls -lh /app/fish/db/xianyu_assistant.db
```

### 2. 确保 Docker 网络存在

nginx 已经在 `app-network`，确认一下：

```bash
docker network inspect app-network >/dev/null 2>&1 && echo "网络已存在" || docker network create app-network
```

### 3. 配置 GitHub Secrets

仓库 → Settings → Secrets and variables → Actions → New repository secret：

| Secret 名称 | 值 | 说明 |
|---|---|---|
| `SERVER_HOST` | 服务器 IP | 例如 `123.45.67.89` |
| `SERVER_USER` | `root` | SSH 登录用户名 |
| `SERVER_SSH_KEY` | 私钥全文 | `~/.ssh/id_ed25519` 的内容，含 BEGIN/END 行 |

### 4. 推送代码触发首次部署

```bash
git add -A
git commit -m "feat: 自动化部署配置 - nginx docker反代 + 前后端分离"
git push origin main
```

到 GitHub 仓库的 **Actions** 页面查看部署进度，成功后访问 `http://fish.oioi.lat`。

---

## 日常部署

```bash
git push origin main
```

GitHub Actions 自动完成全部部署，约 3-5 分钟。也可在 Actions 页面手动触发。

---

## 手动部署（备选）

```bash
bash /app/fish/deploy/deploy.sh
```

> 手动脚本只部署后端，前端需本地 `npm run build` 后 scp 到 `/app/nginx/html`。

---

## 回滚

### 代码回滚

```bash
git revert HEAD
git push origin main
```

或在 GitHub Actions 找到之前成功的 run，点 `Re-run jobs`。

### 镜像回滚

镜像 tag 为版本号（如 `xianyu-assistant:2.0.3`）。升级版本后想回滚，只要旧版本镜像还在：

```bash
# 查看所有镜像
docker images xianyu-assistant

# 用旧版本回滚（替换 <旧版本号>）
docker rm -f xianyu-assistant
docker run -d \
  --name xianyu-assistant \
  --restart unless-stopped \
  --network app-network \
  -p 127.0.0.1:12400:12400 \
  -v /app/fish/db:/app/dbdata \
  -v /app/fish/logs:/app/logs \
  xianyu-assistant:<旧版本号>
docker exec nginx nginx -s reload
```

> 注意：同一版本号多次部署会覆盖该 tag，旧镜像变为 `<none>` 并被自动清理。如需精确回滚到某次提交，建议升级 pom.xml 版本号后再部署。

---

## 常见问题

### 502 Bad Gateway

nginx 连不上后端容器，检查：

```bash
# 后端容器是否在 app-network
docker inspect xianyu-assistant -f '{{json .NetworkSettings.Networks}}'

# nginx 能否解析后端容器名
docker exec nginx wget -qO- http://xianyu-assistant:12400/api/system/version

# 后端容器日志
docker logs xianyu-assistant --tail 50
```

### 前端 404 / 空白

```bash
ls -la /app/nginx/html/
docker exec nginx nginx -t
```

### 接口超时

AI 对话可能超过默认超时，nginx 配置已设 120s。如仍超时，检查后端日志：

```bash
docker logs xianyu-assistant --tail 100
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
| `.github/workflows/deploy.yml` | GitHub Actions 自动化部署流程 |
| `deploy/nginx/fish.oioi.lat.conf` | Nginx 站点配置（容器内路径） |
| `deploy/deploy.sh` | 服务器端手动部署脚本 |
| `deploy/README.md` | 本说明文档 |
| `Dockerfile` | 纯后端 Docker 镜像 |
| `vue-code/vite.config.ts` | 前端构建输出改为 `dist` |
