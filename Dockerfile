# ============================================================
# XianYuAssistant - 纯后端 Docker 镜像（方案 A：前端由 Nginx 独立托管）
# 前端构建：cd vue-code && npm run build，产物在 vue-code/dist，交给 Nginx
# 后端：本镜像，暴露 12400，由 Nginx 反代 /api 和 /ai
# ============================================================

# ===== 构建阶段 =====
FROM eclipse-temurin:21-jdk-alpine AS backend-build

WORKDIR /app

# 安装 Maven
RUN apk add --no-cache maven

# 配置国内 Maven 镜像
RUN mkdir -p /root/.m2 && echo '<?xml version="1.0" encoding="UTF-8"?><settings xmlns="http://maven.apache.org/SETTINGS/1.2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd"><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><name>Huawei Cloud Maven</name><url>https://repo.huaweicloud.com/repository/maven</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 先复制 pom.xml，下载依赖（利用 Docker 缓存，后续构建更快）
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# 复制后端源码
COPY src/ src/

# 构建 JAR（跳过测试）
RUN mvn clean package -DskipTests

# ===== 运行时阶段 =====
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="IAMLZY"
LABEL description="XianYuAssistant - 闲鱼自动化管理系统（后端）"

WORKDIR /app

# 创建数据目录
RUN mkdir -p /app/dbdata /app/logs /app/ms-playwright

# 从构建阶段复制 JAR（用通配符避免版本号硬编码）
COPY --from=backend-build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 12400

# 数据卷声明：数据库和日志持久化
# 运行时请务必挂载：-v xianyu-dbdata:/app/dbdata -v xianyu-logs:/app/logs
VOLUME ["/app/dbdata", "/app/logs"]

# 环境变量
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SERVER_PORT=12400

# 启动命令
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar"]
