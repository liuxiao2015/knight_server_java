package com.knight.server.launcher;

import com.knight.server.common.log.LoggerManager;
import com.knight.server.service.gateway.GatewayServer;
import org.apache.logging.log4j.Logger;

/**
 * 游戏服务器启动器
 * 负责服务器的启动、配置加载和生命周期管理
 * 技术选型：多实例启动 + 进程监控 + 优雅关闭
 * 
 * @author lx
 */
public class GameServerLauncher {
    
    private static final Logger logger = LoggerManager.getLogger(GameServerLauncher.class);
    
    private GatewayServer gatewayServer;
    private volatile boolean running = false;
    
    /**
     * 主启动方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        GameServerLauncher launcher = new GameServerLauncher();
        
        // 添加优雅关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("接收到关闭信号，开始优雅关闭...");
            launcher.shutdown();
        }));
        
        try {
            launcher.start();
        } catch (Exception e) {
            logger.error("服务器启动失败", e);
            System.exit(1);
        }
    }
    
    /**
     * 启动服务器
     * 
     * @throws Exception 启动异常
     */
    public void start() throws Exception {
        logger.info("=== Knight Server 高性能游戏服务器启动中 ===");
        
        // 显示系统信息
        showSystemInfo();
        
        // 启动网关服务器
        startGatewayServer();
        
        running = true;
        logger.info("=== 服务器启动完成，等待客户端连接 ===");
        
        // 保持主线程运行
        keepAlive();
    }
    
    /**
     * 启动网关服务器
     * 
     * @throws Exception 启动异常
     */
    private void startGatewayServer() throws Exception {
        String host = System.getProperty("gateway.host", "0.0.0.0");
        int port = Integer.parseInt(System.getProperty("gateway.port", "9001"));
        
        gatewayServer = new GatewayServer(host, port);
        gatewayServer.start();
        
        logger.info("网关服务器启动成功: {}:{}", host, port);
    }
    
    /**
     * 保持主线程运行
     */
    private void keepAlive() {
        // 启动统计线程
        Thread statsThread = new Thread(this::printStats, "Stats-Thread");
        statsThread.setDaemon(true);
        statsThread.start();
        
        // 主线程等待
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 定期输出统计信息
     */
    private void printStats() {
        while (running) {
            try {
                Thread.sleep(60000); // 每分钟输出一次
                
                if (gatewayServer != null) {
                    var stats = gatewayServer.getStats();
                    logger.info("=== 服务器统计 ===");
                    logger.info("活跃连接: {}", stats.activeConnections());
                    logger.info("总连接数: {}", stats.totalConnections());
                    logger.info("总消息数: {}", stats.totalMessages());
                    logger.info("已路由消息: {}", stats.routedMessages());
                    logger.info("已认证连接: {}", stats.authenticatedConnections());
                    logger.info("===============");
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 显示系统信息
     */
    private void showSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        
        logger.info("系统信息:");
        logger.info("  Java版本: {}", System.getProperty("java.version"));
        logger.info("  JVM版本: {}", System.getProperty("java.vm.version"));
        logger.info("  操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        logger.info("  CPU核心数: {}", runtime.availableProcessors());
        logger.info("  最大内存: {} MB", runtime.maxMemory() / 1024 / 1024);
        logger.info("  总内存: {} MB", runtime.totalMemory() / 1024 / 1024);
        logger.info("  可用内存: {} MB", runtime.freeMemory() / 1024 / 1024);
        logger.info("  工作目录: {}", System.getProperty("user.dir"));
    }
    
    /**
     * 关闭服务器
     */
    public void shutdown() {
        if (!running) {
            return;
        }
        
        logger.info("开始关闭服务器...");
        running = false;
        
        // 关闭网关服务器
        if (gatewayServer != null) {
            gatewayServer.shutdown();
        }
        
        // TODO: 关闭其他服务
        
        logger.info("服务器关闭完成");
    }
}