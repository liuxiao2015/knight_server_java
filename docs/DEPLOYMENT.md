# Knight Server Java - 部署指南

## 快速部署

### 1. 本地开发环境

#### 1.1 环境准备
```bash
# 检查Java版本 (需要17+)
java -version

# 检查Maven版本
mvn -version

# 启动数据库
docker run -d --name knight-mongo -p 27017:27017 mongo:7.0
docker run -d --name knight-redis -p 6379:6379 redis:7.0-alpine
```

#### 1.2 编译部署
```bash
# 克隆项目
git clone https://github.com/liuxiao2015/knight_server_java.git
cd knight_server_java

# 编译项目
mvn clean package -DskipTests

# 启动Gateway服务
java -jar game-server/launcher/target/knight-launcher-1.0.0.jar start gateway

# 启动Logic服务
java -jar game-server/launcher/target/knight-launcher-1.0.0.jar start logic
```

### 2. Docker 容器部署

#### 2.1 单机Docker部署
```bash
# 构建并启动全部服务
docker-compose -f docker/docker-compose.yml up -d

# 查看服务状态
docker-compose -f docker/docker-compose.yml ps

# 查看日志
docker-compose -f docker/docker-compose.yml logs -f gateway
```

#### 2.2 服务访问
- Gateway服务: http://localhost:8080
- Logic服务: http://localhost:9001
- MongoDB: mongodb://localhost:27017
- Redis: redis://localhost:6379
- Grafana监控: http://localhost:3000 (admin/knight123)
- Prometheus: http://localhost:9090

### 3. Kubernetes 生产部署

#### 3.1 集群准备
```bash
# 创建命名空间
kubectl create namespace knight-game

# 创建配置和密钥
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
```

#### 3.2 数据库部署
```bash
# 部署MongoDB
kubectl apply -f k8s/mongodb-deployment.yaml

# 部署Redis
kubectl apply -f k8s/redis-deployment.yaml
```

#### 3.3 应用部署
```bash
# 部署Gateway
kubectl apply -f k8s/gateway-deployment.yaml

# 部署Logic
kubectl apply -f k8s/logic-deployment.yaml

# 检查部署状态
kubectl get pods -n knight-game
kubectl get services -n knight-game
```

#### 3.4 扩容配置
```bash
# Gateway水平扩容到5个实例
kubectl scale deployment knight-gateway --replicas=5 -n knight-game

# Logic服务扩容到3个实例  
kubectl scale deployment knight-logic --replicas=3 -n knight-game

# 查看HPA状态
kubectl get hpa -n knight-game
```

## 配置管理

### 1. 环境配置

#### 1.1 Gateway配置
```properties
# application-gateway.properties
server.port=8080
performance.max-connections=100000
performance.max-requests-per-second=10000
ratelimiter.qps-limit=10000
```

#### 1.2 Logic配置
```properties
# application-logic.properties
server.port=9001
performance.max-online-players=50000
spring.data.mongodb.uri=mongodb://localhost:27017/knight_game
spring.redis.host=localhost
```

### 2. 运行时配置

#### 2.1 JVM参数
```bash
# Gateway服务器
export JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# Logic服务器
export JAVA_OPTS="-Xms4g -Xmx8g -XX:+UseG1GC -XX:+UseStringDeduplication"
```

#### 2.2 系统参数
```bash
# 网络优化
echo 'net.core.somaxconn = 65535' >> /etc/sysctl.conf
echo 'net.ipv4.tcp_max_syn_backlog = 65535' >> /etc/sysctl.conf
echo 'fs.file-max = 1000000' >> /etc/sysctl.conf
sysctl -p

# 文件描述符限制
echo '* soft nofile 1000000' >> /etc/security/limits.conf
echo '* hard nofile 1000000' >> /etc/security/limits.conf
```

## 监控与运维

### 1. 健康检查

#### 1.1 服务健康状态
```bash
# 检查Gateway状态
curl http://localhost:8080/health

# 检查Logic状态  
curl http://localhost:9001/health

# 查看应用指标
curl http://localhost:8080/metrics
curl http://localhost:9001/metrics
```

#### 1.2 数据库健康检查
```bash
# MongoDB连接测试
mongo --eval "db.adminCommand('ismaster')"

# Redis连接测试
redis-cli ping
```

### 2. 性能监控

#### 2.1 应用监控
- **在线用户数**: Logic服务在线玩家统计
- **消息QPS**: Gateway和Logic的消息处理速率
- **响应时间**: 接口响应时间分布
- **错误率**: 异常和错误比例

#### 2.2 系统监控
- **CPU使用率**: 应用和系统CPU利用率
- **内存使用**: JVM堆内存和系统内存
- **网络IO**: 网络连接数和流量
- **磁盘IO**: 数据库读写性能

### 3. 日志管理

#### 3.1 日志收集
```bash
# 查看应用日志
tail -f logs/knight-server.log

# 查看性能日志
tail -f logs/performance.log

# 查看安全日志
tail -f logs/security.log
```

#### 3.2 ELK集成
```yaml
# logstash配置
input {
  file {
    path => "/app/logs/*.log"
    codec => "json"
  }
}
output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "knight-server-%{+YYYY.MM.dd}"
  }
}
```

## 故障排查

### 1. 常见问题

#### 1.1 连接问题
```bash
# 检查端口占用
netstat -tlnp | grep 8080

# 检查防火墙
firewall-cmd --list-ports

# 检查服务状态
systemctl status knight-gateway
```

#### 1.2 性能问题
```bash
# JVM内存分析
jmap -histo <pid>
jstack <pid>

# 系统资源监控
top -p <pid>
iostat -x 1

# 网络连接监控
ss -tuln | grep 8080
```

#### 1.3 数据库问题
```bash
# MongoDB性能分析
db.runCommand({currentOp: true})
db.stats()

# Redis性能监控
redis-cli info
redis-cli monitor
```

### 2. 应急处理

#### 2.1 服务重启
```bash
# 优雅重启Gateway
kubectl rollout restart deployment knight-gateway -n knight-game

# 强制重启Logic
kubectl delete pod -l app=knight-logic -n knight-game
```

#### 2.2 流量控制
```bash
# 临时限流
kubectl patch configmap knight-config -n knight-game -p '{"data":{"ratelimit.qps":"5000"}}'

# 服务降级
kubectl scale deployment knight-logic --replicas=1 -n knight-game
```

#### 2.3 数据备份
```bash
# MongoDB备份
mongodump --host localhost:27017 --db knight_game --out /backup/

# Redis备份
redis-cli BGSAVE
```

## 升级维护

### 1. 版本升级

#### 1.1 滚动升级
```bash
# 更新镜像版本
kubectl set image deployment/knight-gateway gateway=knight/gateway:1.1.0 -n knight-game

# 监控升级状态
kubectl rollout status deployment/knight-gateway -n knight-game

# 回滚版本（如需要）
kubectl rollout undo deployment/knight-gateway -n knight-game
```

#### 1.2 配置更新
```bash
# 热更新配置
kubectl patch configmap knight-config -n knight-game --patch-file config-patch.yaml

# 重启Pod应用新配置
kubectl rollout restart deployment knight-gateway -n knight-game
```

### 2. 容量规划

#### 2.1 资源评估
- **Gateway**: 1000并发 ≈ 100MB内存, 0.1CPU
- **Logic**: 1000在线用户 ≈ 200MB内存, 0.2CPU
- **MongoDB**: 10GB数据 ≈ 4GB内存, 2CPU
- **Redis**: 1GB缓存 ≈ 1.5GB内存, 0.5CPU

#### 2.2 扩容策略
```bash
# 基于CPU自动扩容
kubectl autoscale deployment knight-gateway --cpu-percent=70 --min=3 --max=10 -n knight-game

# 基于内存自动扩容  
kubectl autoscale deployment knight-logic --cpu-percent=75 --min=2 --max=8 -n knight-game
```

---

**维护联系人**: lx  
**紧急联系方式**: 24/7技术支持  
**文档版本**: v1.0.0