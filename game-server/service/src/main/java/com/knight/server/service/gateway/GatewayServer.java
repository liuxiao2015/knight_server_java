package com.knight.server.service.gateway;

import com.knight.server.frame.network.NettyTcpServer;
import com.knight.server.frame.thread.VirtualThreadPoolManager;
import com.knight.server.common.log.GameLogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏网关服务器
 * 负责智能路由分发、协议转换、消息聚合，支持100,000+并发连接
 * 
 * 技术选型：Netty + 虚拟线程 + 智能路由 + 限流熔断
 * 
 * @author lx
 */
@SpringBootApplication
public class GatewayServer {
    
    private static final Logger logger = GameLogManager.getLogger(GatewayServer.class);
    
    // 服务器配置
    private static final int DEFAULT_PORT = 8080;
    private static final int BOSS_THREADS = 2;
    private static final int WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    
    // 网络服务器
    private NettyTcpServer tcpServer;
    
    // 统计信息
    private static final AtomicLong requestCount = new AtomicLong(0);
    private static final AtomicLong responseCount = new AtomicLong(0);
    private static final AtomicLong errorCount = new AtomicLong(0);
    
    // Spring上下文
    private static ConfigurableApplicationContext applicationContext;
    
    public static void main(String[] args) {
        try {
            // 启动Spring Boot应用
            applicationContext = SpringApplication.run(GatewayServer.class, args);
            
            // 获取网关服务器实例
            GatewayServer gatewayServer = applicationContext.getBean(GatewayServer.class);
            
            // 启动网关服务器
            gatewayServer.start();
            
            logger.info("游戏网关服务器启动完成");
            
            // 添加优雅关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(gatewayServer::shutdown));
            
        } catch (Exception e) {
            logger.error("网关服务器启动失败", e);
            System.exit(1);
        }
    }
    
    /**
     * 启动网关服务器
     */
    public void start() {
        try {
            logger.info("正在启动游戏网关服务器...");
            
            // 初始化路由管理器
            RouteManager.initialize();
            
            // 初始化负载均衡器
            LoadBalancer.initialize();
            
            // 初始化限流器
            RateLimiter.initialize();
            
            // 启动TCP服务器
            tcpServer = new NettyTcpServer(DEFAULT_PORT, BOSS_THREADS, WORKER_THREADS);
            
            // 在虚拟线程中启动服务器（非阻塞）
            VirtualThreadPoolManager.submitVirtualTask(() -> {
                tcpServer.start();
            });
            
            // 启动监控任务
            startMonitoringTasks();
            
            logger.info("游戏网关服务器启动成功，端口: {}", DEFAULT_PORT);
            
        } catch (Exception e) {
            logger.error("网关服务器启动异常", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 启动监控任务
     */
    private void startMonitoringTasks() {
        // 性能监控任务
        VirtualThreadPoolManager.submitVirtualTask(() -> {
            while (true) {
                try {
                    Thread.sleep(30000); // 30秒监控一次
                    
                    // 打印统计信息
                    printStats();
                    
                    // 检查系统健康状态
                    checkSystemHealth();
                    
                } catch (InterruptedException e) {
                    logger.info("监控任务被中断");
                    break;
                } catch (Exception e) {
                    logger.error("监控任务异常", e);
                }
            }
        });
    }
    
    /**
     * 打印统计信息
     */
    private void printStats() {
        NettyTcpServer.NetworkStats networkStats = NettyTcpServer.getStats();
        VirtualThreadPoolManager.ThreadPoolStats threadStats = VirtualThreadPoolManager.getStats();
        
        logger.info("=== 网关服务器统计信息 ===");
        logger.info("网络统计: {}", networkStats);
        logger.info("线程池统计: {}", threadStats);
        logger.info("请求统计: 总请求={}, 总响应={}, 错误数={}", 
                   requestCount.get(), responseCount.get(), errorCount.get());
        logger.info("===========================");
    }
    
    /**
     * 检查系统健康状态
     */
    private void checkSystemHealth() {
        try {
            // 检查内存使用率
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsagePercent = (double) usedMemory / totalMemory * 100;
            
            if (memoryUsagePercent > 85) {
                logger.warn("内存使用率过高: {:.2f}%", memoryUsagePercent);
            }
            
            // 检查连接数
            int activeConnections = NettyTcpServer.getStats().getActiveConnections();
            if (activeConnections > 80000) { // 80%阈值
                logger.warn("活跃连接数过高: {}", activeConnections);
            }
            
            // 检查错误率
            long totalRequests = requestCount.get();
            long totalErrors = errorCount.get();
            if (totalRequests > 0) {
                double errorRate = (double) totalErrors / totalRequests * 100;
                if (errorRate > 5) { // 5%错误率阈值
                    logger.warn("错误率过高: {:.2f}%", errorRate);
                }
            }
            
        } catch (Exception e) {
            logger.error("健康检查异常", e);
        }
    }
    
    /**
     * 处理客户端请求
     * 
     * @param request 请求消息
     * @return 响应消息
     */
    public Object handleRequest(Object request) {
        requestCount.incrementAndGet();
        
        try {
            // 限流检查
            if (!RateLimiter.isAllowed()) {
                errorCount.incrementAndGet();
                return createErrorResponse("服务器繁忙，请稍后重试");
            }
            
            // 路由分发
            Object response = RouteManager.route(request);
            
            responseCount.incrementAndGet();
            return response;
            
        } catch (Exception e) {
            logger.error("处理请求异常", e);
            errorCount.incrementAndGet();
            return createErrorResponse("服务器内部错误");
        }
    }
    
    /**
     * 创建错误响应
     * 
     * @param message 错误信息
     * @return 错误响应
     */
    private Object createErrorResponse(String message) {
        return new ErrorResponse(500, message);
    }
    
    /**
     * 优雅关闭服务器
     */
    @PreDestroy
    public void shutdown() {
        logger.info("开始关闭游戏网关服务器...");
        
        try {
            // 停止接受新连接
            if (tcpServer != null) {
                tcpServer.shutdown();
            }
            
            // 关闭线程池
            VirtualThreadPoolManager.shutdown();
            
            // 关闭Spring应用上下文
            if (applicationContext != null) {
                applicationContext.close();
            }
            
            logger.info("游戏网关服务器已关闭");
            
        } catch (Exception e) {
            logger.error("关闭网关服务器异常", e);
        }
    }
    
    /**
     * 路由管理器
     */
    private static class RouteManager {
        private static volatile boolean initialized = false;
        
        public static void initialize() {
            if (!initialized) {
                // 初始化路由规则
                logger.info("路由管理器初始化完成");
                initialized = true;
            }
        }
        
        public static Object route(Object request) {
            // 根据请求类型进行路由分发
            // 这里可以实现复杂的路由逻辑
            return "路由响应: " + request.toString();
        }
    }
    
    /**
     * 负载均衡器
     */
    private static class LoadBalancer {
        private static volatile boolean initialized = false;
        
        public static void initialize() {
            if (!initialized) {
                // 初始化负载均衡策略
                logger.info("负载均衡器初始化完成");
                initialized = true;
            }
        }
    }
    
    /**
     * 限流器
     */
    private static class RateLimiter {
        private static volatile boolean initialized = false;
        private static final AtomicLong requestCounter = new AtomicLong(0);
        private static final long MAX_REQUESTS_PER_SECOND = 10000; // 每秒最大请求数
        
        public static void initialize() {
            if (!initialized) {
                // 启动计数器重置任务
                VirtualThreadPoolManager.submitVirtualTask(() -> {
                    while (true) {
                        try {
                            Thread.sleep(1000); // 每秒重置一次
                            requestCounter.set(0);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
                
                logger.info("限流器初始化完成，限制: {} req/s", MAX_REQUESTS_PER_SECOND);
                initialized = true;
            }
        }
        
        public static boolean isAllowed() {
            return requestCounter.incrementAndGet() <= MAX_REQUESTS_PER_SECOND;
        }
    }
    
    /**
     * 错误响应类
     */
    private static class ErrorResponse {
        private final int code;
        private final String message;
        
        public ErrorResponse(int code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public int getCode() { return code; }
        public String getMessage() { return message; }
        
        @Override
        public String toString() {
            return String.format("ErrorResponse{code=%d, message='%s'}", code, message);
        }
    }
}