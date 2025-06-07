# 项目架构详细设计文档

## 1. 整体架构

Knight Server Java 采用微服务架构，具备以下特点：

### 1.1 性能指标
- **Gateway服务器**: 100,000+ 并发连接
- **Logic服务器**: 50,000+ 在线用户
- **消息延迟**: 99.9% < 100ms
- **可用性**: 99.99% SLA

### 1.2 技术栈
- **核心语言**: Java 17 (生产环境兼容)
- **应用框架**: Spring Boot 3.x
- **网络框架**: Netty 4.x
- **数据存储**: MongoDB + Redis
- **消息队列**: Disruptor
- **容器化**: Docker + Kubernetes

## 2. 模块详细设计

### 2.1 Common 通用模块

#### 2.1.1 日志管理 (GameLogManager)
```java
// 高性能异步日志，支持动态级别调整
GameLogManager.logPerformance("login", 50, true);
GameLogManager.logBusiness("player", "levelup", 12345, "Level 10->11");
GameLogManager.logSecurity("login_fail", "192.168.1.1", "Wrong password");
```

**特性:**
- Log4j2异步日志处理
- 按日期和大小自动分割
- 支持ELK集成
- 动态日志级别调整

#### 2.1.2 配置管理 (ConfigDataManager)
```java
// 支持热加载的配置管理
GameConfig config = ConfigDataManager.loadConfig("game.json", GameConfig.class);
long version = ConfigDataManager.getConfigVersion("game.json");
```

**特性:**
- JSON配置文件支持
- 文件监听热加载
- 版本控制机制
- 配置校验

#### 2.1.3 工具类
- **雪花算法ID生成器**: 分布式唯一ID生成
- **JSON工具类**: 高性能序列化/反序列化
- **加密工具类**: AES-256 + RSA

### 2.2 Frame 框架模块

#### 2.2.1 线程模型 (VirtualThreadPoolManager)
```java
// Java 17兼容的高性能线程池
VirtualThreadPoolManager.submitVirtualTask(() -> {
    // I/O密集型任务
});
VirtualThreadPoolManager.submitCpuTask(() -> {
    // CPU密集型任务
});
```

**特性:**
- ForkJoinPool优化的I/O处理
- 传统线程池处理CPU密集任务
- 线程监控和统计
- 优雅的异常处理

#### 2.2.2 事件系统 (HighPerformanceEventBus)
```java
// Disruptor超高性能事件处理
eventBus.publishEvent("player.login", playerId);
eventBus.publishHighPriorityEvent("battle.result", result);
```

**特性:**
- Disruptor环形缓冲区
- 事件优先级支持
- 无锁并发处理
- 事件持久化和重试

#### 2.2.3 网络通信 (NettyTcpServer)
```java
// 支持10万+并发的TCP服务器
NettyTcpServer server = new NettyTcpServer(8080, 2, 16);
server.start();
NettyTcpServer.broadcast(message); // 广播消息
```

**特性:**
- Netty NIO高性能网络
- TCP长连接管理
- 心跳检测机制
- Snappy消息压缩
- 流量控制

#### 2.2.4 消息编解码 (GameMessageCodec)
```java
// 高效的消息协议
GameMessage msg = new GameMessage(MessageType.LOGIN_REQUEST, data);
```

**协议格式:**
```
[魔数 4字节][消息类型 4字节][序列号 8字节][时间戳 8字节][压缩标志 1字节][长度 4字节][消息体 N字节]
```

### 2.3 Service 业务模块

#### 2.3.1 Gateway 网关服务
```java
@SpringBootApplication
public class GatewayServer {
    // 智能路由、负载均衡、限流熔断
}
```

**功能:**
- 智能路由分发
- 多种负载均衡算法
- 令牌桶限流
- 熔断降级机制
- WebSocket支持

#### 2.3.2 Logic 逻辑服务
```java
// 核心游戏逻辑处理
LogicServer.handlePlayerLogin(playerId, token);
int online = LogicServer.getOnlinePlayerCount();
```

**系统模块:**
- 玩家系统 (登录、属性、等级)
- 背包系统 (物品管理、批量操作)
- 战斗系统 (实时战斗、技能系统)
- 聊天系统 (多频道、敏感词过滤)

### 2.4 Launcher 启动器
```java
// 统一服务管理
java -jar launcher.jar start gateway
java -jar launcher.jar status
```

**特性:**
- 多实例管理
- 交互式控制面板
- 进程监控
- 自动重启

## 3. 部署架构

### 3.1 Docker 容器化
```yaml
# docker-compose.yml
services:
  gateway:
    build: ./docker/Dockerfile.gateway
    ports: ["8080:8080"]
  logic:
    build: ./docker/Dockerfile.logic
    ports: ["9001:9001"]
  mongodb:
    image: mongo:7.0
  redis:
    image: redis:7.0-alpine
```

### 3.2 Kubernetes 编排
```yaml
# gateway-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: knight-gateway
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: gateway
        image: knight/gateway:1.0.0
        resources:
          limits:
            memory: "6Gi"
            cpu: "2000m"
```

**特性:**
- HPA自动扩缩容
- 健康检查
- 滚动更新
- 服务发现

### 3.3 监控体系
```yaml
# 监控栈
- Prometheus: 指标采集
- Grafana: 可视化仪表板
- ELK: 日志分析
- Zipkin: 链路追踪
```

## 4. 性能优化

### 4.1 JVM 调优
```bash
# Gateway JVM参数
-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100

# Logic JVM参数
-Xms4g -Xmx8g -XX:+UseG1GC -XX:+UseStringDeduplication
```

### 4.2 网络优化
```bash
# 系统参数调优
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
```

### 4.3 数据库优化
```javascript
// MongoDB连接池
{
  "maxPoolSize": 100,
  "minPoolSize": 10,
  "connectTimeoutMS": 5000
}
```

## 5. 安全设计

### 5.1 认证授权
- JWT Token认证
- RSA密钥交换
- AES-256数据加密

### 5.2 防护机制
- 防重放攻击
- IP黑白名单
- QPS限流
- 异常登录检测

### 5.3 审计日志
- 安全事件记录
- 操作链路追踪
- 异常行为监控

## 6. 扩展性设计

### 6.1 水平扩展
- 无状态服务设计
- 数据分片策略
- 缓存集群

### 6.2 垂直扩展
- 模块化插件架构
- 热插拔组件
- 动态配置更新

## 7. 运维管理

### 7.1 监控告警
- 实时性能监控
- 阈值告警
- 自动故障转移

### 7.2 运维工具
- 服务健康检查
- 配置热更新
- 灰度发布支持

---

**项目作者**: lx  
**技术架构**: 微服务 + 容器化 + 云原生  
**设计目标**: 生产级 + 高性能 + 高可用