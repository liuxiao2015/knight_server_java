# 逻辑服务Dockerfile
# 功能说明：构建核心游戏逻辑Docker镜像，支持50K+在线玩家
# 技术选型：OpenJDK 17 + 多阶段构建优化镜像大小
# @author lx

# 构建阶段
FROM maven:3.9.5-openjdk-17-slim AS builder

# 设置工作目录
WORKDIR /app

# 复制pom文件先下载依赖
COPY pom.xml .
COPY game-common/pom.xml game-common/
COPY game-frame/pom.xml game-frame/
COPY game-service-api/pom.xml game-service-api/
COPY game-logic/pom.xml game-logic/

# 下载依赖
RUN mvn dependency:go-offline -B

# 复制源代码
COPY game-common/src game-common/src
COPY game-frame/src game-frame/src
COPY game-service-api/src game-service-api/src
COPY game-logic/src game-logic/src

# 编译打包
RUN mvn clean package -DskipTests -pl game-logic -am

# 运行阶段
FROM openjdk:17-jdk-slim

# 设置环境变量
ENV JAVA_OPTS="-Xms4g -Xmx8g -XX:+UseG1GC -XX:+UseStringDeduplication"
ENV SPRING_PROFILES_ACTIVE=prod

# 创建应用用户
RUN groupadd -r logic && useradd -r -g logic logic

# 创建应用目录
WORKDIR /app
RUN mkdir -p logs data

# 复制JAR文件
COPY --from=builder /app/game-logic/target/game-logic-*.jar app.jar

# 复制配置文件
COPY docker/logic/application-docker.properties application-docker.properties

# 设置权限
RUN chown -R logic:logic /app

# 切换到应用用户
USER logic

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9001/actuator/health || exit 1

# 暴露端口
EXPOSE 9001

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.config.additional-location=application-docker.properties"]