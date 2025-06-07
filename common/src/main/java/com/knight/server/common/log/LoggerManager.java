package com.knight.server.common.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 高性能异步日志管理器
 * 基于Log4j2实现的异步日志系统，支持动态日志级别调整
 * 技术选型：Log4j2 AsyncLogger + Disruptor
 * 
 * @author lx
 */
public class LoggerManager {
    
    private static final ConcurrentMap<String, Logger> LOGGER_CACHE = new ConcurrentHashMap<>();
    private static final Logger SYSTEM_LOGGER = org.apache.logging.log4j.LogManager.getLogger("SYSTEM");
    private static final Logger PERFORMANCE_LOGGER = org.apache.logging.log4j.LogManager.getLogger("PERFORMANCE");
    private static final Logger BUSINESS_LOGGER = org.apache.logging.log4j.LogManager.getLogger("BUSINESS");
    
    /**
     * 获取指定名称的Logger
     * 使用缓存机制提高性能
     * 
     * @param name Logger名称
     * @return Logger实例
     */
    public static Logger getLogger(String name) {
        return LOGGER_CACHE.computeIfAbsent(name, org.apache.logging.log4j.LogManager::getLogger);
    }
    
    /**
     * 获取类对应的Logger
     * 
     * @param clazz 类
     * @return Logger实例
     */
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }
    
    /**
     * 获取系统日志Logger
     * 用于记录系统级别的重要事件
     * 
     * @return 系统日志Logger
     */
    public static Logger getSystemLogger() {
        return SYSTEM_LOGGER;
    }
    
    /**
     * 获取性能监控Logger
     * 独立输出性能相关日志，便于监控分析
     * 
     * @return 性能监控Logger
     */
    public static Logger getPerformanceLogger() {
        return PERFORMANCE_LOGGER;
    }
    
    /**
     * 获取业务日志Logger
     * 用于记录业务逻辑相关的日志
     * 
     * @return 业务日志Logger
     */
    public static Logger getBusinessLogger() {
        return BUSINESS_LOGGER;
    }
    
    /**
     * 动态调整日志级别
     * 支持运行时调整指定Logger的日志级别
     * 
     * @param loggerName Logger名称
     * @param level 新的日志级别
     */
    public static void setLogLevel(String loggerName, String level) {
        LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
        loggerConfig.setLevel(Level.valueOf(level.toUpperCase()));
        ctx.updateLoggers();
        
        SYSTEM_LOGGER.info("动态调整Logger[{}]日志级别为[{}]", loggerName, level);
    }
    
    /**
     * 获取当前日志级别
     * 
     * @param loggerName Logger名称
     * @return 当前日志级别
     */
    public static String getLogLevel(String loggerName) {
        LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
        return loggerConfig.getLevel().toString();
    }
    
    /**
     * 记录性能指标
     * 
     * @param operation 操作名称
     * @param duration 执行时间(毫秒)
     * @param params 额外参数
     */
    public static void recordPerformance(String operation, long duration, Object... params) {
        PERFORMANCE_LOGGER.info("PERF|{}|{}ms|{}", operation, duration, params);
    }
}