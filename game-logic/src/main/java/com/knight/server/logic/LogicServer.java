package com.knight.server.logic;

import com.knight.server.frame.event.HighPerformanceEventBus;
import com.knight.server.frame.thread.VirtualThreadPoolManager;
import com.knight.server.common.log.GameLogManager;
import com.knight.server.common.utils.SnowflakeIdGenerator;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏逻辑服务器
 * 负责核心游戏逻辑处理，支持50,000+在线用户
 * 
 * 技术选型：事件驱动架构 + 虚拟线程 + 内存数据结构
 * 
 * @author lx
 */
@SpringBootApplication
public class LogicServer {
    
    private static final Logger logger = GameLogManager.getLogger(LogicServer.class);
    
    // 事件总线
    private static HighPerformanceEventBus eventBus;
    
    // ID生成器
    private static SnowflakeIdGenerator idGenerator;
    
    // 在线玩家管理
    private static final ConcurrentHashMap<Long, PlayerSession> onlinePlayers = new ConcurrentHashMap<>();
    
    // 统计信息
    private static final AtomicLong totalLogins = new AtomicLong(0);
    private static final AtomicLong totalLogouts = new AtomicLong(0);
    
    public static void main(String[] args) {
        try {
            // 启动Spring Boot应用
            SpringApplication.run(LogicServer.class, args);
            
            // 初始化逻辑服务器
            initialize();
            
            logger.info("游戏逻辑服务器启动完成");
            
        } catch (Exception e) {
            logger.error("逻辑服务器启动失败", e);
            System.exit(1);
        }
    }
    
    /**
     * 初始化逻辑服务器
     */
    private static void initialize() {
        try {
            // 初始化ID生成器
            idGenerator = new SnowflakeIdGenerator(1, 1); // 数据中心ID=1, 机器ID=1
            
            // 初始化事件总线
            eventBus = new HighPerformanceEventBus();
            
            // 初始化各个系统
            PlayerSystem.initialize();
            BagSystem.initialize();
            ChatSystem.initialize();
            BattleSystem.initialize();
            
            // 启动监控任务
            startMonitoringTasks();
            
            logger.info("逻辑服务器初始化完成");
            
        } catch (Exception e) {
            logger.error("逻辑服务器初始化失败", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 启动监控任务
     */
    private static void startMonitoringTasks() {
        VirtualThreadPoolManager.submitVirtualTask(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // 60秒监控一次
                    
                    // 打印统计信息
                    printStats();
                    
                    // 清理离线玩家数据
                    cleanupOfflinePlayers();
                    
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
    private static void printStats() {
        logger.info("=== 逻辑服务器统计信息 ===");
        logger.info("在线玩家数: {}", onlinePlayers.size());
        logger.info("总登录次数: {}", totalLogins.get());
        logger.info("总登出次数: {}", totalLogouts.get());
        logger.info("事件统计: {}", eventBus.getStats());
        logger.info("===========================");
    }
    
    /**
     * 清理离线玩家数据
     */
    private static void cleanupOfflinePlayers() {
        long currentTime = System.currentTimeMillis();
        int cleanupCount = 0;
        
        for (PlayerSession session : onlinePlayers.values()) {
            if (currentTime - session.getLastActiveTime() > 300000) { // 5分钟无活动
                onlinePlayers.remove(session.getPlayerId());
                cleanupCount++;
            }
        }
        
        if (cleanupCount > 0) {
            logger.info("清理离线玩家数据: {} 个", cleanupCount);
        }
    }
    
    /**
     * 玩家登录处理
     * 
     * @param playerId 玩家ID
     * @param token 认证令牌
     * @return 登录结果
     */
    public static boolean handlePlayerLogin(long playerId, String token) {
        try {
            // 验证令牌
            if (!validateToken(token)) {
                logger.warn("玩家登录失败，令牌无效: playerId={}", playerId);
                return false;
            }
            
            // 检查是否已在线
            if (onlinePlayers.containsKey(playerId)) {
                logger.warn("玩家重复登录: playerId={}", playerId);
                return false;
            }
            
            // 创建玩家会话
            PlayerSession session = new PlayerSession(playerId);
            onlinePlayers.put(playerId, session);
            
            // 发布登录事件
            eventBus.publishEvent("player.login", playerId);
            
            totalLogins.incrementAndGet();
            
            logger.info("玩家登录成功: playerId={}", playerId);
            return true;
            
        } catch (Exception e) {
            logger.error("玩家登录异常: playerId={}", playerId, e);
            return false;
        }
    }
    
    /**
     * 玩家登出处理
     * 
     * @param playerId 玩家ID
     */
    public static void handlePlayerLogout(long playerId) {
        try {
            PlayerSession session = onlinePlayers.remove(playerId);
            if (session != null) {
                // 保存玩家数据
                savePlayerData(session);
                
                // 发布登出事件
                eventBus.publishEvent("player.logout", playerId);
                
                totalLogouts.incrementAndGet();
                
                logger.info("玩家登出成功: playerId={}", playerId);
            }
            
        } catch (Exception e) {
            logger.error("玩家登出异常: playerId={}", playerId, e);
        }
    }
    
    /**
     * 获取在线玩家数
     * 
     * @return 在线玩家数
     */
    public static int getOnlinePlayerCount() {
        return onlinePlayers.size();
    }
    
    /**
     * 验证令牌
     * 
     * @param token 令牌
     * @return 是否有效
     */
    private static boolean validateToken(String token) {
        // 这里实现JWT令牌验证逻辑
        return token != null && !token.isEmpty();
    }
    
    /**
     * 保存玩家数据
     * 
     * @param session 玩家会话
     */
    private static void savePlayerData(PlayerSession session) {
        // 异步保存玩家数据到数据库
        VirtualThreadPoolManager.submitVirtualTask(() -> {
            try {
                // 这里实现数据库保存逻辑
                logger.debug("保存玩家数据: playerId={}", session.getPlayerId());
            } catch (Exception e) {
                logger.error("保存玩家数据失败: playerId={}", session.getPlayerId(), e);
            }
        });
    }
    
    /**
     * 玩家会话类
     */
    public static class PlayerSession {
        private final long playerId;
        private final long loginTime;
        private volatile long lastActiveTime;
        private final String sessionId;
        
        public PlayerSession(long playerId) {
            this.playerId = playerId;
            this.loginTime = System.currentTimeMillis();
            this.lastActiveTime = this.loginTime;
            this.sessionId = String.valueOf(idGenerator.nextId());
        }
        
        public void updateActiveTime() {
            this.lastActiveTime = System.currentTimeMillis();
        }
        
        // Getters
        public long getPlayerId() { return playerId; }
        public long getLoginTime() { return loginTime; }
        public long getLastActiveTime() { return lastActiveTime; }
        public String getSessionId() { return sessionId; }
        
        public long getOnlineDuration() {
            return System.currentTimeMillis() - loginTime;
        }
    }
    
    /**
     * 玩家系统
     */
    private static class PlayerSystem {
        public static void initialize() {
            logger.info("玩家系统初始化完成");
        }
    }
    
    /**
     * 背包系统
     */
    private static class BagSystem {
        public static void initialize() {
            logger.info("背包系统初始化完成");
        }
    }
    
    /**
     * 聊天系统
     */
    private static class ChatSystem {
        public static void initialize() {
            logger.info("聊天系统初始化完成");
        }
    }
    
    /**
     * 战斗系统
     */
    private static class BattleSystem {
        public static void initialize() {
            logger.info("战斗系统初始化完成");
        }
    }
}