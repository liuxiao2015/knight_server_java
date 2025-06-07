# 使用OpenJDK 17作为基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 复制Maven构建产物
COPY launcher/target/knight-server-launcher-*.jar app.jar
COPY common/target/knight-server-common-*.jar libs/
COPY frame/target/knight-server-frame-*.jar libs/
COPY service/target/knight-server-service-*.jar libs/

# 创建日志目录
RUN mkdir -p /app/logs

# 设置JVM参数
ENV JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+OptimizeStringConcat"

# 网关服务端口
EXPOSE 9001

# 管理端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp app.jar:libs/* com.knight.server.launcher.GameServerLauncher"]