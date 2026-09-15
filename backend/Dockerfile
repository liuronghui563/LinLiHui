# =============================================================================
# 邻里汇后端 —— 多阶段构建 Dockerfile（JDK 17）
#
# 7 个 Spring Boot 服务共用本文件，用 --build-arg SERVICE=<模块目录名> 选择模块：
#   gateway / auth-service / aid-service / community-service
#   ad-service / file-service / notify-service
#
# 注意：构建上下文必须是 backend/（父 POM 所在目录），不能是仓库根目录。
# common 与 api 是**模块内依赖**，缺少同级模块的 POM 时 Maven 反应堆无法解析，
# -pl/-am 会直接报 "Could not find artifact com.chengqu:common:jar"。
#
# 单独构建某个服务：
#   docker build -t chengqu-huzhu/gateway:latest --build-arg SERVICE=gateway backend/
#
# 依赖下载写进 BuildKit 缓存挂载（需 BuildKit，Docker 20.10+ 与 docker compose v2 默认开启），
# 因此重复构建不会重新下载依赖，只重新编译。
# =============================================================================

# --------------------------------- 构建阶段 ----------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build

# 要构建的模块目录名
ARG SERVICE
# Maven 镜像加速地址；设为空字符串即使用官方中央仓库
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public

RUN test -n "$SERVICE" \
 || { echo "ERROR: 必须指定 --build-arg SERVICE=<模块目录名>" >&2; exit 1; }

WORKDIR /build

# 可选：写入镜像加速 settings.xml（路径在缓存挂载之外，会留在镜像层里）
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
        mkdir -p /root/.m2 && \
        printf '%s' \
          "<settings><mirrors><mirror><id>mirror</id><mirrorOf>central</mirrorOf><url>$MAVEN_MIRROR_URL</url></mirror></mirrors></settings>" \
          > /root/.m2/settings.xml; \
    fi

# 只复制源码；target/ 与 .idea/ 已由 .dockerignore 排除，避免几百 MB 的构建产物进上下文
COPY . .

# -pl <模块> -am：连同它依赖的 common / api 一起构建，其余模块不参与编译。
# -Dmaven.test.skip=true：测试在本地/CI 用 mvn test 跑，构建镜像只负责产出可运行 jar。
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl "$SERVICE" -am clean package -Dmaven.test.skip=true \
 && cp "/build/$SERVICE/target/$SERVICE-"*.jar /build/app.jar

# --------------------------------- 运行阶段 ----------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

ARG SERVICE
# 仅用于 EXPOSE 声明与本文件的健康检查端口对齐，不改变 Spring Boot 的监听端口
ARG SERVER_PORT=8080
ARG TZ=Asia/Shanghai

ENV TZ=${TZ} \
    SERVER_PORT=${SERVER_PORT} \
    SERVICE_NAME=${SERVICE} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

# curl 仅用于 compose 的 healthcheck；tzdata 保证容器内时区为 Asia/Shanghai
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime \
 && echo "$TZ" > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar

# 非 root 运行
RUN groupadd -g 1000 app \
 && useradd -u 1000 -g app -M -s /usr/sbin/nologin app \
 && chown -R app:app /app
USER app

EXPOSE ${SERVER_PORT}

# 用 exec 让 java 成为 PID 1，才能正确收到 docker stop 的 SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
