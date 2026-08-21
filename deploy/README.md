# 自动化部署说明

## 架构

```
开发者本地 git push -> GitHub Actions
                         |- 构建前端并上传到服务器
                         |- 构建后端镜像并推送到北京 ACR
                         `- SSH 到服务器
                              |- 拉取 ACR 镜像并标记为 fish:<版本号>
                              |- 停止旧容器，启动并健康检查新容器
                              `- 重载 Nginx
```

- ACR 地址默认是 `registry.cn-beijing.aliyuncs.com`。
- 镜像 tag 由 `pom.xml` 中的项目版本号动态生成。
- SSH 命令最长执行 40 分钟，镜像拉取会自动重试 3 次。
- Dockerfile 使用 Spring Boot 分层镜像；首次拉取完成后，后续会复用 JRE 和 Maven 依赖层。
- 拉取的镜像会标记为本地 `fish:<版本号>`，例如 `fish:2.0.3`；旧容器停止后才会启动新容器。
- 新容器通过 `GET /api/system/version` 健康检查后才会重载 Nginx；失败时会尝试恢复上一个镜像。

## 首次配置

### 1. 登录阿里云并创建 ACR 仓库

1. 浏览器登录阿里云控制台，搜索并打开“容器镜像服务 ACR”。
2. 左上角地域选择“华北 2（北京）”。
3. 进入“命名空间”，创建一个名称，例如 `glser`。
4. 进入“镜像仓库”，在该命名空间下创建 **私有** 仓库，名称必须填写 `xianyu-assistant`。
5. 在 ACR 左侧菜单进入“访问凭证”或“Registry 登录凭证”，创建/设置 Docker 登录密码，记录页面显示的用户名和设置的密码。

最终镜像地址将是：

```text
registry.cn-beijing.aliyuncs.com/glser/xianyu-assistant:2.0.3
```

其中 `glser` 是第 3 步创建的命名空间，替换为你的实际名称。

### 2. 在服务器登录 ACR

SSH 登录服务器后执行：

```bash
docker login registry.cn-beijing.aliyuncs.com
```

按提示输入第 1 步 ACR 页面显示的 Docker 用户名和密码。出现 `Login Succeeded` 后完成。该登录只需执行一次，Docker 会保存凭证。

> 如果你的服务器是阿里云北京 ECS，优先复制 ACR 仓库页面“操作指南 > Docker 登录”提供的 VPC 地址和命令；然后将该地址设置为下面的 `ACR_REGISTRY`。

### 3. 添加 GitHub Actions 配置

仓库打开 **Settings -> Secrets and variables -> Actions**。

在 **Variables** 中添加：

| 名称 | 值 | 是否必须 |
|---|---|---|
| `ACR_NAMESPACE` | 你的 ACR 命名空间，例如 `glser` | 是 |
| `ACR_REGISTRY` | ACR 地址，例如 `registry.cn-beijing.aliyuncs.com` | 否，不设置时默认北京公网地址 |

在 **Secrets** 中添加：

| 名称 | 值 | 用途 |
|---|---|---|
| `ACR_USERNAME` | 第 1 步 ACR 页面显示的 Docker 用户名 | GitHub Actions 推送镜像 |
| `ACR_PASSWORD` | 第 1 步创建的 Docker 登录密码 | GitHub Actions 推送镜像 |
| `SERVER_HOST` | 服务器公网 IP 或域名 | SSH/SCP 连接 |
| `SERVER_USER` | SSH 用户，例如 `root` | SSH/SCP 登录 |
| `SERVER_SSH_KEY` | SSH 私钥全文，包含 BEGIN/END 行 | SSH/SCP 身份验证 |

`ACR_NAMESPACE` 是你自定义并在 ACR 创建的名称。`ACR_USERNAME` 和 `ACR_PASSWORD` 不是自定义文本，必须来自 ACR 的登录凭证页面。

### 4. 准备服务器目录和 Docker 网络

```bash
mkdir -p /app/fish/db /app/fish/logs /app/nginx/html/fish.oioi.lat
docker network inspect app-network >/dev/null 2>&1 || docker network create app-network
```

### 5. 提交并部署

```bash
git add .github/workflows/deploy.yml Dockerfile deploy
git commit -m "ci: deploy backend through Beijing ACR"
git push origin main
```

## 日常部署

每次发布前递增 `pom.xml` 中的版本号，然后推送：

```bash
git push origin main
```

重复推送同一个版本号会覆盖旧镜像，无法按该版本号回滚到旧构建。

## 手动部署与回滚

服务器手动部署指定版本：

```bash
ACR_NAMESPACE=glser bash /path/to/deploy.sh 2.0.4
```

若使用 ACR VPC 地址：

```bash
ACR_REGISTRY=<ACR控制台提供的VPC地址> ACR_NAMESPACE=glser bash /path/to/deploy.sh 2.0.4
```

回滚到已发布版本：

```bash
ACR_NAMESPACE=glser bash /path/to/deploy.sh 2.0.3
```

脚本会保留 `/app/fish/db` 和 `/app/fish/logs` 中的数据。

## 常见问题

### 登录或拉取 ACR 失败

```bash
docker logout registry.cn-beijing.aliyuncs.com
docker login registry.cn-beijing.aliyuncs.com
docker pull registry.cn-beijing.aliyuncs.com/<命名空间>/xianyu-assistant:<版本号>
```

确认服务器、GitHub Secrets 和 ACR 仓库使用的是同一个地域地址和相同命名空间。

### 新容器健康检查失败

```bash
docker logs xianyu-assistant --tail 100
docker inspect xianyu-assistant --format '{{json .State.Health}}'
```

### 502 Bad Gateway

```bash
docker inspect xianyu-assistant -f '{{json .NetworkSettings.Networks}}'
docker exec nginx wget -qO- http://xianyu-assistant:12400/api/system/version
docker logs xianyu-assistant --tail 100
```

## 文件清单

| 文件 | 作用 |
|---|---|
| `.github/workflows/deploy.yml` | GitHub Actions 自动部署，构建、推送 ACR、SSH 发布 |
| `Dockerfile` | Spring Boot 分层后端镜像和健康检查 |
| `deploy/deploy.sh` | 服务器手动部署/回滚脚本 |
| `deploy/nginx/fish.oioi.lat.conf` | Nginx 站点配置 |
