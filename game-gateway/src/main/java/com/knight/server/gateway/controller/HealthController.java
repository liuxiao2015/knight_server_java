package com.knight.server.gateway.controller;

import com.knight.server.gateway.service.RateLimiterService;
import com.knight.server.gateway.service.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关健康检查控制器
 * 提供网关服务的健康状态和监控信息
 * 
 * 功能说明：
 * - 服务健康状态检查
 * - 连接数统计
 * - 限流状态监控
 * - 系统资源使用情况
 * 
 * 技术选型：Spring Boot RestController
 * 
 * @author lx
 */
@RestController
@RequestMapping("/actuator")
public class HealthController {
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private RateLimiterService rateLimiterService;
    
    /**
     * 基础健康检查
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        
        // 检查关键组件状态
        health.put("components", getComponentsHealth());
        
        return health;
    }
    
    /**
     * 获取详细监控指标
     * 
     * @return 监控指标
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 连接统计
        Map<String, Object> connections = new HashMap<>();
        connections.put("active", sessionManager.getActiveConnectionCount());
        connections.put("total", sessionManager.getTotalConnectionCount());
        metrics.put("connections", connections);
        
        // 限流统计
        RateLimiterService.RateLimitStats rateLimitStats = rateLimiterService.getStats();
        Map<String, Object> rateLimit = new HashMap<>();
        rateLimit.put("totalRequests", rateLimitStats.getTotalRequests());
        rateLimit.put("blockedRequests", rateLimitStats.getBlockedRequests());
        rateLimit.put("blockRate", String.format("%.2f%%", rateLimitStats.getBlockRate()));
        rateLimit.put("activeBuckets", rateLimitStats.getActiveBucketsCount());
        rateLimit.put("activeUserBuckets", rateLimitStats.getActiveUserBucketsCount());
        metrics.put("rateLimit", rateLimit);
        
        // 系统资源
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> system = new HashMap<>();
        system.put("cpuCores", runtime.availableProcessors());
        system.put("totalMemory", runtime.totalMemory());
        system.put("freeMemory", runtime.freeMemory());
        system.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        system.put("maxMemory", runtime.maxMemory());
        system.put("memoryUsage", String.format("%.2f%%", 
                (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100));
        metrics.put("system", system);
        
        return metrics;
    }
    
    /**
     * 获取服务信息
     * 
     * @return 服务信息
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("app", "knight-gateway");
        info.put("description", "Game Gateway Service - High-performance API gateway supporting 100K+ concurrent connections");
        info.put("version", "1.0.0-SNAPSHOT");
        info.put("author", "lx");
        info.put("buildTime", System.getProperty("build.time", "unknown"));
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("springBootVersion", org.springframework.boot.SpringBootVersion.getVersion());
        
        return info;
    }
    
    /**
     * 获取组件健康状态
     * 
     * @return 组件状态列表
     */
    private Map<String, Object> getComponentsHealth() {
        Map<String, Object> components = new HashMap<>();
        
        // 会话管理器状态
        Map<String, Object> sessionManagerHealth = new HashMap<>();
        sessionManagerHealth.put("status", "UP");
        sessionManagerHealth.put("activeConnections", sessionManager.getActiveConnectionCount());
        components.put("sessionManager", sessionManagerHealth);
        
        // 限流器状态
        Map<String, Object> rateLimiterHealth = new HashMap<>();
        rateLimiterHealth.put("status", "UP");
        RateLimiterService.RateLimitStats stats = rateLimiterService.getStats();
        rateLimiterHealth.put("blockRate", String.format("%.2f%%", stats.getBlockRate()));
        components.put("rateLimiter", rateLimiterHealth);
        
        // 内存状态检查
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100;
        Map<String, Object> memoryHealth = new HashMap<>();
        memoryHealth.put("status", memoryUsage < 85 ? "UP" : "WARNING");
        memoryHealth.put("usage", String.format("%.2f%%", memoryUsage));
        components.put("memory", memoryHealth);
        
        return components;
    }
}