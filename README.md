# Knight Server - 高性能游戏服务器框架

## 项目概述

Knight Server是一个基于Java 17的高性能游戏服务器框架，采用现代化的微服务架构设计，支持大规模并发连接和高吞吐量消息处理。

### 技术栈

- **语言**: Java 17
- **框架**: Spring Boot 3.x
- **网络**: Netty 4.x
- **数据库**: MongoDB
- **缓存**: Redis + Caffeine
- **消息队列**: Disruptor
- **协议**: Protocol Buffers
- **认证**: JWT Token
- **容器化**: Docker + Kubernetes

### 性能目标

- **Gateway**: 单实例支持 100,000+ 并发连接
- **Logic**: 单实例支持 50,000+ 在线用户
- **延迟**: 消息处理延迟 < 10ms (99.9%)
- **吞吐量**: 单实例 100,000+ QPS

## 架构设计

### 模块结构

```
game-server/
├── common/          # 通用模块
│   ├── log/         # 日志管理
│   ├── data/        # 数据管理
│   ├── utils/       # 工具类
│   └── proto/       # 协议定义
├── frame/           # 框架模块
│   ├── thread/      # 线程管理
│   ├── security/    # 安全模块
│   ├── event/       # 事件系统
│   ├── network/     # 网络模块
│   ├── rpc/         # RPC框架
│   ├── cache/       # 缓存模块
│   ├── data/        # 数据访问
│   └── timer/       # 定时器
├── service/         # 业务模块
│   ├── gateway/     # 网关服务
│   ├── logic/       # 逻辑服务
│   ├── chat/        # 聊天服务
│   └── payment/     # 支付服务
├── launcher/        # 启动器
├── admin/          # 管理后台
└── test/           # 测试模块
```

### 核心特性

#### 1. 高性能线程模型

- **I/O线程池**: 处理网络I/O和数据库操作
- **CPU线程池**: 处理计算密集型任务
- **优先级调度**: 支持任务优先级处理
- **监控统计**: 实时线程池状态监控

#### 2. 异步事件系统

- **Disruptor**: 基于LMAX Disruptor的高性能事件总线
- **事件优先级**: 支持高/中/低优先级事件处理
- **处理链**: 支持事件处理链模式
- **异常处理**: 完善的异常处理和重试机制

#### 3. 网络通信

- **长连接管理**: TCP长连接池管理
- **心跳检测**: 自动心跳检测和断线重连
- **消息压缩**: Snappy压缩减少网络开销
- **流量控制**: 自适应流量控制和限流

#### 4. 安全认证

- **JWT Token**: 基于JWT的无状态认证
- **AES加密**: AES-256数据加密
- **防重放**: 防重放攻击机制
- **限流**: 基于令牌桶的QPS限流

## 快速开始

### 环境要求

- Java 17+
- Maven 3.6+
- MongoDB 4.4+
- Redis 6.0+

### 编译构建

```bash
# 克隆项目
git clone https://github.com/liuxiao2015/knight_server_java.git
cd knight_server_java

# 编译项目
mvn clean compile

# 打包项目
mvn clean package
```

### 运行服务器

```bash
# 方式1: 直接运行
java -jar launcher/target/knight-server-launcher-1.0.0-SNAPSHOT.jar

# 方式2: 使用Docker
docker-compose -f docker/docker-compose.yml up -d

# 方式3: 使用Kubernetes
kubectl apply -f k8s/
```

### 配置参数

主要配置参数：

```properties
# 网关配置
gateway.host=0.0.0.0
gateway.port=9001

# 数据库配置
spring.data.mongodb.uri=mongodb://localhost:27017/knight_game

# Redis配置
spring.redis.host=localhost
spring.redis.port=6379

# JVM参数
JAVA_OPTS=-Xms1g -Xmx4g -XX:+UseG1GC
```

## 开发指南

### 创建新的服务

1. 在 `service` 模块下创建新的包
2. 实现业务逻辑接口
3. 注册消息处理器
4. 添加路由配置

### 添加新的协议

1. 在 `common/proto` 下定义Protocol Buffer消息
2. 编译生成Java类
3. 在消息处理器中添加处理逻辑

### 性能优化建议

1. **线程池配置**: 根据业务特点调整线程池大小
2. **缓存策略**: 合理使用本地缓存和分布式缓存
3. **数据库优化**: 使用合适的索引和查询优化
4. **网络优化**: 启用消息压缩和批量处理

## 监控与运维

### 健康检查

- **端点**: `http://localhost:8080/health`
- **指标**: `http://localhost:8080/metrics`
- **Prometheus**: `http://localhost:8080/prometheus`

### 日志管理

- **系统日志**: `logs/system.log`
- **性能日志**: `logs/performance.log`
- **业务日志**: `logs/business.log`
- **错误日志**: `logs/error.log`

### 性能指标

重要监控指标：

- 连接数 (活跃/总数)
- 消息吞吐量 (接收/发送)
- 响应延迟 (平均/P99)
- 内存使用率
- CPU使用率
- 线程池状态

## 部署架构

### 单机部署

适用于开发和测试环境：

```
[Client] -> [Gateway] -> [Logic/Chat/Payment]
              |
         [MongoDB/Redis]
```

### 集群部署

适用于生产环境：

```
[LoadBalancer] -> [Gateway Cluster] -> [Logic Cluster]
                       |                    |
                  [Chat Cluster]      [Payment Cluster]
                       |                    |
                 [MongoDB Cluster] <- [Redis Cluster]
```

### Kubernetes部署

使用Kubernetes进行容器编排：

- **自动扩缩容**: 基于CPU/内存使用率自动扩缩容
- **服务发现**: 自动服务注册和发现
- **健康检查**: 自动故障检测和重启
- **滚动更新**: 零停机时间更新

## 贡献指南

### 代码规范

遵循阿里巴巴Java开发手册：

- 类名使用UpperCamelCase
- 方法名使用lowerCamelCase
- 常量名使用UPPER_CASE
- 包名使用小写字母

### 提交规范

提交信息格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

类型说明：
- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- style: 代码格式调整
- refactor: 重构
- test: 测试相关
- chore: 构建/工具相关

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 联系方式

- 作者: lx
- 邮箱: [待补充]
- 项目地址: https://github.com/liuxiao2015/knight_server_java

## 更新日志

### v1.0.0 (2024-06-06)

- 初始版本发布
- 实现基础框架架构
- 支持高性能网络通信
- 集成JWT认证和安全机制
- 提供Docker和Kubernetes部署支持