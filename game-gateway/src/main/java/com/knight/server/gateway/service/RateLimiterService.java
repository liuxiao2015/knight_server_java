package com.knight.server.gateway.service;

import com.knight.server.common.log.GameLogManager;
import com.knight.server.gateway.config.GatewayProperties;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流服务
 * 基于令牌桶算法实现的高性能限流器
 * 
 * 功能说明：
 * - 全局QPS限流
 * - 单IP限流
 * - 单用户限流  
 * - 动态限流规则调整
 * - 限流统计和监控
 * 
 * 技术选型：令牌桶算法 + ConcurrentHashMap + AtomicLong
 * 
 * @author lx
 */
@Service
public class RateLimiterService {
    
    private static final Logger logger = GameLogManager.getLogger(RateLimiterService.class);
    
    @Autowired
    private GatewayProperties gatewayProperties;
    
    /**
     * 全局令牌桶
     */
    private final TokenBucket globalTokenBucket = new TokenBucket();
    
    /**
     * IP限流桶集合
     */
    private final ConcurrentHashMap<String, TokenBucket> ipTokenBuckets = new ConcurrentHashMap<>();
    
    /**
     * 用户限流桶集合
     */
    private final ConcurrentHashMap<Long, TokenBucket> userTokenBuckets = new ConcurrentHashMap<>();
    
    /**
     * 限流统计
     */
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong blockedRequests = new AtomicLong(0);
    
    /**
     * 检查全局限流
     * 
     * @return true表示允许通过，false表示被限流
     */
    public boolean allowGlobalRequest() {
        if (!gatewayProperties.getRatelimiter().isEnabled()) {
            return true;
        }
        
        totalRequests.incrementAndGet();
        
        // 获取配置参数
        GatewayProperties.RateLimiterConfig config = gatewayProperties.getRatelimiter();
        
        // 初始化全局令牌桶（如果未初始化）
        if (!globalTokenBucket.isInitialized()) {
            globalTokenBucket.init(config.getQpsLimit(), config.getBurstCapacity(), config.getRefillPeriodMs());
        }
        
        boolean allowed = globalTokenBucket.tryAcquire();
        if (!allowed) {
            blockedRequests.incrementAndGet();
            logger.warn("全局限流触发: QPS超过限制 {}", config.getQpsLimit());
        }
        
        return allowed;
    }
    
    /**
     * 检查IP限流
     * 
     * @param clientIp 客户端IP
     * @return true表示允许通过，false表示被限流
     */
    public boolean allowIpRequest(String clientIp) {
        if (!gatewayProperties.getRatelimiter().isEnabled()) {
            return true;
        }
        
        // 单IP限流（QPS为全局的1/10）
        int ipQpsLimit = gatewayProperties.getRatelimiter().getQpsLimit() / 10;
        
        TokenBucket ipBucket = ipTokenBuckets.computeIfAbsent(clientIp, k -> {
            TokenBucket bucket = new TokenBucket();
            bucket.init(ipQpsLimit, ipQpsLimit * 2, 1000);
            return bucket;
        });
        
        boolean allowed = ipBucket.tryAcquire();
        if (!allowed) {
            logger.warn("IP限流触发: IP={}, QPS超过限制 {}", clientIp, ipQpsLimit);
        }
        
        return allowed;
    }
    
    /**
     * 检查用户限流
     * 
     * @param userId 用户ID
     * @return true表示允许通过，false表示被限流
     */
    public boolean allowUserRequest(Long userId) {
        if (!gatewayProperties.getRatelimiter().isEnabled() || userId == null) {
            return true;
        }
        
        // 单用户限流（QPS为全局的1/100）
        int userQpsLimit = Math.max(gatewayProperties.getRatelimiter().getQpsLimit() / 100, 10);
        
        TokenBucket userBucket = userTokenBuckets.computeIfAbsent(userId, k -> {
            TokenBucket bucket = new TokenBucket();
            bucket.init(userQpsLimit, userQpsLimit * 2, 1000);
            return bucket;
        });
        
        boolean allowed = userBucket.tryAcquire();
        if (!allowed) {
            logger.warn("用户限流触发: userId={}, QPS超过限制 {}", userId, userQpsLimit);
        }
        
        return allowed;
    }
    
    /**
     * 获取限流统计信息
     * 
     * @return 限流统计
     */
    public RateLimitStats getStats() {
        return new RateLimitStats(
            totalRequests.get(),
            blockedRequests.get(),
            ipTokenBuckets.size(),
            userTokenBuckets.size()
        );
    }
    
    /**
     * 清理过期的限流桶
     * 定期调用此方法清理长时间不使用的限流桶
     */
    public void cleanupExpiredBuckets() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 5 * 60 * 1000; // 5分钟过期
        
        // 清理IP限流桶
        ipTokenBuckets.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getLastAccessTime() > expireTime);
        
        // 清理用户限流桶
        userTokenBuckets.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getLastAccessTime() > expireTime);
        
        logger.debug("清理限流桶完成: IP桶数量={}, 用户桶数量={}", 
                    ipTokenBuckets.size(), userTokenBuckets.size());
    }
    
    /**
     * 令牌桶实现
     */
    private static class TokenBucket {
        private volatile long capacity;          // 桶容量
        private volatile long tokens;            // 当前令牌数
        private volatile long refillRate;        // 每秒补充令牌数
        private volatile long lastRefillTime;    // 上次补充时间
        private volatile long lastAccessTime;    // 上次访问时间
        private volatile boolean initialized = false;
        
        public void init(long refillRate, long capacity, long refillPeriodMs) {
            this.refillRate = refillRate;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
            this.lastAccessTime = lastRefillTime;
            this.initialized = true;
        }
        
        public boolean isInitialized() {
            return initialized;
        }
        
        public synchronized boolean tryAcquire() {
            if (!initialized) {
                return false;
            }
            
            long currentTime = System.currentTimeMillis();
            lastAccessTime = currentTime;
            
            // 补充令牌
            refillTokens(currentTime);
            
            // 尝试获取令牌
            if (tokens > 0) {
                tokens--;
                return true;
            }
            
            return false;
        }
        
        private void refillTokens(long currentTime) {
            long elapsedTime = currentTime - lastRefillTime;
            if (elapsedTime > 0) {
                // 计算应该补充的令牌数
                long tokensToAdd = (elapsedTime * refillRate) / 1000;
                if (tokensToAdd > 0) {
                    tokens = Math.min(capacity, tokens + tokensToAdd);
                    lastRefillTime = currentTime;
                }
            }
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }
    
    /**
     * 限流统计信息
     */
    public static class RateLimitStats {
        private final long totalRequests;
        private final long blockedRequests;
        private final int activeBucketsCount;
        private final int activeUserBucketsCount;
        
        public RateLimitStats(long totalRequests, long blockedRequests, 
                             int activeBucketsCount, int activeUserBucketsCount) {
            this.totalRequests = totalRequests;
            this.blockedRequests = blockedRequests;
            this.activeBucketsCount = activeBucketsCount;
            this.activeUserBucketsCount = activeUserBucketsCount;
        }
        
        public long getTotalRequests() { return totalRequests; }
        public long getBlockedRequests() { return blockedRequests; }
        public int getActiveBucketsCount() { return activeBucketsCount; }
        public int getActiveUserBucketsCount() { return activeUserBucketsCount; }
        
        public double getBlockRate() {
            return totalRequests > 0 ? (double) blockedRequests / totalRequests * 100 : 0;
        }
    }
}