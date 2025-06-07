package com.knight.server.common.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.Level;

/**
 * 高性能异步日志管理器
 * 基于Log4j2实现异步日志，支持动态调整日志级别
 * 
 * 技术选型：Log4j2异步日志 + 文件分割 + ELK集成
 * 
 * @author lx
 */
public class GameLogManager {
    
    private static final Logger logger = LogManager.getLogger(GameLogManager.class);
    private static LoggerContext context;
    
    static {
        // 初始化日志上下文
        context = (LoggerContext) LogManager.getContext(false);
    }
    
    /**
     * 获取指定类的Logger
     * 
     * @param clazz 类对象
     * @return Logger实例
     */
    public static Logger getLogger(Class<?> clazz) {
        return LogManager.getLogger(clazz);
    }
    
    /**
     * 获取指定名称的Logger
     * 
     * @param name Logger名称
     * @return Logger实例
     */
    public static Logger getLogger(String name) {
        return LogManager.getLogger(name);
    }
    
    /**
     * 动态调整日志级别
     * 
     * @param loggerName Logger名称
     * @param level 日志级别
     */
    public static void setLogLevel(String loggerName, Level level) {
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
        loggerConfig.setLevel(level);
        context.updateLoggers();
        
        logger.info("已更新Logger[{}]的日志级别为[{}]", loggerName, level);
    }
    
    /**
     * 获取当前日志级别
     * 
     * @param loggerName Logger名称
     * @return 当前日志级别
     */
    public static Level getLogLevel(String loggerName) {
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
        return loggerConfig.getLevel();
    }
    
    /**
     * 性能监控日志记录
     * 
     * @param operation 操作名称
     * @param duration 执行时长(毫秒)
     * @param success 是否成功
     */
    public static void logPerformance(String operation, long duration, boolean success) {
        Logger perfLogger = LogManager.getLogger("PERFORMANCE");
        perfLogger.info("Operation: {}, Duration: {}ms, Success: {}", 
                       operation, duration, success);
    }
    
    /**
     * 业务日志记录
     * 
     * @param module 模块名称
     * @param action 操作动作
     * @param userId 用户ID
     * @param details 详细信息
     */
    public static void logBusiness(String module, String action, long userId, String details) {
        Logger bizLogger = LogManager.getLogger("BUSINESS");
        bizLogger.info("Module: {}, Action: {}, UserId: {}, Details: {}", 
                      module, action, userId, details);
    }
    
    /**
     * 安全日志记录
     * 
     * @param event 安全事件
     * @param ip 客户端IP
     * @param details 详细信息
     */
    public static void logSecurity(String event, String ip, String details) {
        Logger secLogger = LogManager.getLogger("SECURITY");
        secLogger.warn("SecurityEvent: {}, IP: {}, Details: {}", event, ip, details);
    }
}