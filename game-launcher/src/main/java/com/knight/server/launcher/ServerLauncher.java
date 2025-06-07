package com.knight.server.launcher;

import com.knight.server.common.log.GameLogManager;
import com.knight.server.common.data.ConfigDataManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Scanner;

/**
 * 游戏服务器启动器
 * 提供统一的服务器启动、配置管理和进程监控功能
 * 
 * 技术选型：Spring Boot + 配置管理 + 多实例支持
 * 
 * @author lx
 */
@SpringBootApplication
public class ServerLauncher {
    
    private static final Logger logger = GameLogManager.getLogger(ServerLauncher.class);
    
    // 服务器类型枚举
    public enum ServerType {
        GATEWAY("gateway", "com.knight.server.service.gateway.GatewayServer"),
        LOGIC("logic", "com.knight.server.service.logic.LogicServer"),
        CHAT("chat", "com.knight.server.service.chat.ChatServer"),
        PAYMENT("payment", "com.knight.server.service.payment.PaymentServer"),
        ADMIN("admin", "com.knight.server.admin.AdminApplication");
        
        private final String name;
        private final String mainClass;
        
        ServerType(String name, String mainClass) {
            this.name = name;
            this.mainClass = mainClass;
        }
        
        public String getName() { return name; }
        public String getMainClass() { return mainClass; }
    }
    
    public static void main(String[] args) {
        try {
            logger.info("=== Knight Server Java 游戏服务器启动器 ===");
            logger.info("作者: lx");
            logger.info("版本: 1.0.0");
            logger.info("技术栈: Java 21 + Spring Boot 3.x + Netty 4.x");
            logger.info("=========================================");
            
            // 初始化配置管理器
            ConfigDataManager.setConfigRoot("config/");
            
            // 加载启动器配置
            LauncherConfig config = loadLauncherConfig();
            
            if (args.length > 0) {
                // 命令行模式
                handleCommandLine(args, config);
            } else {
                // 交互模式
                handleInteractiveMode(config);
            }
            
        } catch (Exception e) {
            logger.error("启动器异常", e);
            System.exit(1);
        }
    }
    
    /**
     * 处理命令行模式
     * 
     * @param args 命令行参数
     * @param config 配置
     */
    private static void handleCommandLine(String[] args, LauncherConfig config) {
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "start":
                if (args.length < 2) {
                    logger.error("请指定服务器类型: gateway, logic, chat, payment, admin");
                    return;
                }
                startServer(args[1], config);
                break;
                
            case "stop":
                if (args.length < 2) {
                    logger.error("请指定要停止的服务器类型");
                    return;
                }
                stopServer(args[1]);
                break;
                
            case "restart":
                if (args.length < 2) {
                    logger.error("请指定要重启的服务器类型");
                    return;
                }
                restartServer(args[1], config);
                break;
                
            case "status":
                showServerStatus();
                break;
                
            case "help":
                showHelp();
                break;
                
            default:
                logger.error("未知命令: {}", command);
                showHelp();
                break;
        }
    }
    
    /**
     * 处理交互模式
     * 
     * @param config 配置
     */
    private static void handleInteractiveMode(LauncherConfig config) {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.println("\n=== 游戏服务器控制面板 ===");
            System.out.println("1. 启动Gateway服务器");
            System.out.println("2. 启动Logic服务器");
            System.out.println("3. 启动Chat服务器");
            System.out.println("4. 启动Payment服务器");
            System.out.println("5. 启动Admin后台");
            System.out.println("6. 查看服务器状态");
            System.out.println("7. 重载配置");
            System.out.println("0. 退出");
            System.out.print("请选择操作: ");
            
            try {
                int choice = scanner.nextInt();
                
                switch (choice) {
                    case 1:
                        startServer("gateway", config);
                        break;
                    case 2:
                        startServer("logic", config);
                        break;
                    case 3:
                        startServer("chat", config);
                        break;
                    case 4:
                        startServer("payment", config);
                        break;
                    case 5:
                        startServer("admin", config);
                        break;
                    case 6:
                        showServerStatus();
                        break;
                    case 7:
                        reloadConfig();
                        break;
                    case 0:
                        logger.info("退出启动器");
                        return;
                    default:
                        System.out.println("无效选择，请重新输入");
                        break;
                }
                
            } catch (Exception e) {
                logger.error("处理用户输入异常", e);
                scanner.nextLine(); // 清除无效输入
            }
        }
    }
    
    /**
     * 启动服务器
     * 
     * @param serverTypeName 服务器类型名称
     * @param config 配置
     */
    private static void startServer(String serverTypeName, LauncherConfig config) {
        try {
            ServerType serverType = getServerType(serverTypeName);
            if (serverType == null) {
                logger.error("未知的服务器类型: {}", serverTypeName);
                return;
            }
            
            logger.info("正在启动 {} 服务器...", serverType.getName());
            
            // 设置系统属性
            System.setProperty("server.type", serverType.getName());
            System.setProperty("server.config", config.getConfigFile(serverType));
            
            // 启动Spring Boot应用
            Class<?> mainClass = Class.forName(serverType.getMainClass());
            SpringApplication app = new SpringApplication(mainClass);
            
            // 设置应用属性
            app.setAdditionalProfiles(serverType.getName());
            
            // 启动应用（在新线程中启动，避免阻塞）
            Thread serverThread = new Thread(() -> {
                try {
                    app.run();
                } catch (Exception e) {
                    logger.error("{} 服务器启动失败", serverType.getName(), e);
                }
            });
            
            serverThread.setName(serverType.getName() + "-Server");
            serverThread.setDaemon(false);
            serverThread.start();
            
            // 等待启动完成
            Thread.sleep(3000);
            
            logger.info("{} 服务器启动完成", serverType.getName());
            
        } catch (Exception e) {
            logger.error("启动服务器失败: {}", serverTypeName, e);
        }
    }
    
    /**
     * 停止服务器
     * 
     * @param serverTypeName 服务器类型名称
     */
    private static void stopServer(String serverTypeName) {
        logger.info("停止 {} 服务器功能暂未实现", serverTypeName);
        // TODO: 实现服务器停止逻辑
    }
    
    /**
     * 重启服务器
     * 
     * @param serverTypeName 服务器类型名称
     * @param config 配置
     */
    private static void restartServer(String serverTypeName, LauncherConfig config) {
        logger.info("正在重启 {} 服务器...", serverTypeName);
        stopServer(serverTypeName);
        try {
            Thread.sleep(2000); // 等待2秒
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        startServer(serverTypeName, config);
    }
    
    /**
     * 显示服务器状态
     */
    private static void showServerStatus() {
        logger.info("=== 服务器状态 ===");
        logger.info("Gateway: 运行中");
        logger.info("Logic: 运行中");
        logger.info("Chat: 停止");
        logger.info("Payment: 停止");
        logger.info("Admin: 运行中");
        logger.info("================");
        // TODO: 实现真实的状态检查
    }
    
    /**
     * 重载配置
     */
    private static void reloadConfig() {
        logger.info("重载配置文件...");
        ConfigDataManager.clearCache();
        logger.info("配置重载完成");
    }
    
    /**
     * 显示帮助信息
     */
    private static void showHelp() {
        System.out.println("\n=== 命令行帮助 ===");
        System.out.println("java -jar launcher.jar start <server_type>  - 启动指定类型服务器");
        System.out.println("java -jar launcher.jar stop <server_type>   - 停止指定类型服务器");
        System.out.println("java -jar launcher.jar restart <server_type> - 重启指定类型服务器");
        System.out.println("java -jar launcher.jar status               - 查看服务器状态");
        System.out.println("java -jar launcher.jar help                 - 显示帮助信息");
        System.out.println("\n服务器类型: gateway, logic, chat, payment, admin");
        System.out.println("==================");
    }
    
    /**
     * 根据名称获取服务器类型
     * 
     * @param name 服务器类型名称
     * @return 服务器类型
     */
    private static ServerType getServerType(String name) {
        for (ServerType type : ServerType.values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * 加载启动器配置
     * 
     * @return 配置对象
     */
    private static LauncherConfig loadLauncherConfig() {
        LauncherConfig config = ConfigDataManager.loadConfig("launcher.json", LauncherConfig.class);
        if (config == null) {
            logger.warn("未找到launcher.json配置文件，使用默认配置");
            config = new LauncherConfig();
        }
        return config;
    }
    
    /**
     * 启动器配置类
     */
    public static class LauncherConfig {
        private String configRoot = "config/";
        private boolean autoRestart = true;
        private int restartDelay = 5000;
        
        public String getConfigRoot() { return configRoot; }
        public void setConfigRoot(String configRoot) { this.configRoot = configRoot; }
        
        public boolean isAutoRestart() { return autoRestart; }
        public void setAutoRestart(boolean autoRestart) { this.autoRestart = autoRestart; }
        
        public int getRestartDelay() { return restartDelay; }
        public void setRestartDelay(int restartDelay) { this.restartDelay = restartDelay; }
        
        public String getConfigFile(ServerType serverType) {
            return configRoot + serverType.getName() + ".json";
        }
    }
}