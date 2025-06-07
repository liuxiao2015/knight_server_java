# Knight Server Java - 生产级高性能游戏服务器框架

![License](https://img.shields.io/badge/license-MIT-green)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![Netty](https://img.shields.io/badge/Netty-4.x-blue)

**作者：** lx  
**版本：** 1.0.0  
**技术栈：** Java 21 + Spring Boot 3.x + Netty 4.x + MongoDB + Redis

## 📖 项目概述

Knight Server Java 是一个生产级的高性能游戏服务器框架，专为支持大规模在线游戏而设计。该框架采用现代化的微服务架构，具备优秀的性能表现和完善的功能特性。

### 🎯 性能目标
- **Gateway实例**: 支持 100,000+ 并发连接
- **Logic实例**: 支持 50,000+ 在线用户
- **消息延迟**: 99.9% < 100ms
- **可用性**: 99.99% SLA

### 🏗️ 核心架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│    Gateway      │───▶│     Logic       │───▶│   Database      │
│   (100K+ 连接)  │    │  (50K+ 在线)    │    │   (MongoDB)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│      Chat       │    │    Payment      │    │     Redis       │
│   (聊天服务)     │    │   (支付服务)     │    │   (缓存服务)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🚀 快速开始

### 环境要求
- Java 21+
- Maven 3.8+
- MongoDB 4.4+
- Redis 6.0+
- Docker & Kubernetes (可选)

### 本地启动

1. **克隆项目**
```bash
git clone https://github.com/liuxiao2015/knight_server_java.git
cd knight_server_java
```

2. **编译项目**
```bash
mvn clean compile
```

3. **启动数据库**
```bash
# 启动MongoDB
mongod --dbpath /data/db

# 启动Redis
redis-server
```

4. **启动服务器**
```bash
# 方式1: 使用启动器
java -jar game-server/launcher/target/knight-launcher-1.0.0.jar

# 方式2: 直接启动特定服务
java -jar game-server/launcher/target/knight-launcher-1.0.0.jar start gateway
java -jar game-server/launcher/target/knight-launcher-1.0.0.jar start logic
```

### Docker 部署

```bash
# 构建镜像
docker-compose -f docker/docker-compose.yml build

# 启动全套服务
docker-compose -f docker/docker-compose.yml up -d
```

### Kubernetes 部署

```bash
# 创建命名空间
kubectl create namespace knight-game

# 部署应用
kubectl apply -f k8s/gateway-deployment.yaml
kubectl apply -f k8s/logic-deployment.yaml
```

## 📁 项目结构

```
knight_server_java/
├── game-server/                 # 游戏服务器核心
│   ├── common/                  # 通用模块
│   │   ├── log/                # 日志管理
│   │   ├── data/               # 数据管理
│   │   ├── utils/              # 工具类
│   │   └── proto/              # 协议定义
│   ├── frame/                   # 框架模块
│   │   ├── thread/             # 线程管理
│   │   ├── security/           # 安全模块
│   │   ├── event/              # 事件系统
│   │   ├── network/            # 网络通信
│   │   ├── rpc/                # RPC框架
│   │   ├── cache/              # 缓存模块
│   │   ├── database/           # 数据库
│   │   └── timer/              # 定时器
│   ├── service/                 # 业务服务
│   │   ├── gateway/            # 网关服务
│   │   ├── logic/              # 逻辑服务
│   │   ├── chat/               # 聊天服务
│   │   └── payment/            # 支付服务
│   ├── launcher/                # 启动器
│   ├── admin/                   # 管理后台
│   └── test/                    # 测试框架
├── docker/                      # Docker配置
├── k8s/                         # Kubernetes配置
└── docs/                        # 项目文档
```

## 🔧 核心模块

### 1. Common 通用模块

#### 日志管理
- 基于 Log4j2 异步日志
- 支持动态日志级别调整
- 自动日志分割和清理
- ELK 集成支持

#### 数据管理
- JSON/Excel 配置文件支持
- 热加载机制
- 版本控制
- 配置验证

#### 工具类
- 雪花算法 ID 生成器
- JSON 序列化工具
- 时间工具类
- 加密解密工具

### 2. Frame 框架模块

#### 线程模型
- Java 21 虚拟线程池
- 智能任务调度
- 线程监控统计
- 异常处理机制

#### 网络通信
- Netty 4.x NIO 实现
- TCP 长连接管理
- 心跳检测
- 消息压缩 (Snappy)

#### 事件系统
- Disruptor 高性能事件总线
- 事件优先级支持
- 异步事件处理
- 事件监控统计

#### 安全模块
- JWT Token 认证
- AES-256 数据加密
- RSA 密钥交换
- 防重放攻击
- IP 黑白名单
- QPS 限流

### 3. Service 业务模块

#### Gateway 网关
- 智能路由分发
- 负载均衡
- 限流熔断
- 协议转换
- WebSocket 支持

#### Logic 逻辑服务
- 玩家系统
- 背包系统
- 战斗系统
- 活动系统
- 邮件系统

#### Chat 聊天服务
- 私聊/世界聊天
- 敏感词过滤
- 聊天记录存储
- 表情包支持

#### Payment 支付服务
- 多平台支付支持
- 订单管理
- 支付回调处理
- 风控检测

## 🎮 游戏系统

### 玩家系统
```java
// 玩家登录
boolean success = LogicServer.handlePlayerLogin(playerId, token);

// 获取在线玩家数
int onlineCount = LogicServer.getOnlinePlayerCount();
```

### 事件系统
```java
// 发布事件
eventBus.publishEvent("player.login", playerId);
eventBus.publishHighPriorityEvent("battle.result", battleResult);
```

### 配置管理
```java
// 加载配置
GameConfig config = ConfigDataManager.loadConfig("game.json", GameConfig.class);

// 热重载
GameConfig newConfig = ConfigDataManager.reloadConfig("game.json", GameConfig.class);
```

## 📊 性能监控

### JVM 监控
- 内存使用率监控
- GC 性能监控
- 线程池状态监控

### 业务监控
- 在线玩家数统计
- 消息处理性能
- 错误率监控

### 系统监控
- CPU/内存使用率
- 网络连接数
- 数据库连接池

## 🔧 配置说明

### Gateway 配置
```properties
# 网络配置
network.boss-threads=2
network.worker-threads=16
performance.max-connections=100000

# 限流配置
ratelimiter.qps-limit=10000
ratelimiter.burst-capacity=15000
```

### Logic 配置
```properties
# 数据库配置
spring.data.mongodb.uri=mongodb://localhost:27017/knight_game
spring.redis.host=localhost

# 性能配置
performance.max-online-players=50000
performance.save-interval=300000
```

## 🚀 部署指南

### 单机部署
适用于开发和测试环境
```bash
# 启动全部服务
./scripts/start-all.sh

# 停止全部服务
./scripts/stop-all.sh
```

### 集群部署
适用于生产环境
```bash
# 使用 Docker Compose
docker-compose -f docker/docker-compose.yml up -d

# 使用 Kubernetes
kubectl apply -f k8s/
```

### 扩容指南
```bash
# Gateway 水平扩容
kubectl scale deployment knight-gateway --replicas=5

# Logic 服务扩容
kubectl scale deployment knight-logic --replicas=4
```

## 📈 性能调优

### JVM 调优
```bash
# Gateway 服务器 JVM 参数
-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100

# Logic 服务器 JVM 参数  
-Xms4g -Xmx8g -XX:+UseG1GC -XX:+UseStringDeduplication
```

### 网络调优
```bash
# 系统级网络参数调优
echo 'net.core.somaxconn = 65535' >> /etc/sysctl.conf
echo 'net.ipv4.tcp_max_syn_backlog = 65535' >> /etc/sysctl.conf
sysctl -p
```

### 数据库调优
```javascript
// MongoDB 连接池配置
{
  "maxPoolSize": 100,
  "minPoolSize": 10,
  "maxIdleTimeMS": 60000,
  "connectTimeoutMS": 5000
}
```

## 🧪 测试

### 单元测试
```bash
mvn test
```

### 集成测试
```bash
mvn verify -P integration-test
```

### 压力测试
```bash
# 启动压力测试工具
java -jar game-server/test/target/knight-test-1.0.0.jar benchmark
```

## 📝 开发规范

### 代码规范
- 遵循阿里巴巴 Java 开发手册
- 所有公共方法必须添加 JavaDoc
- 作者标识统一使用 `@author lx`

### 提交规范
```
feat: 添加新功能
fix: 修复bug
docs: 更新文档
style: 代码格式调整
refactor: 代码重构
test: 添加测试
chore: 构建工具修改
```

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交代码 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目基于 MIT 许可证开源 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 👥 作者

**lx** - *项目发起人* - [GitHub](https://github.com/liuxiao2015)

## 🙏 致谢

- [Netty](https://netty.io/) - 高性能网络框架
- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架
- [Disruptor](https://lmax-exchange.github.io/disruptor/) - 高性能并发框架
- [MongoDB](https://www.mongodb.com/) - 文档数据库
- [Redis](https://redis.io/) - 内存数据库

---

如有问题或建议，欢迎提交 Issue 或 Pull Request！