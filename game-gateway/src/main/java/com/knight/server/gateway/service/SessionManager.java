package com.knight.server.gateway.service;

import com.knight.server.common.log.GameLogManager;
import io.netty.channel.Channel;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话管理服务
 * 管理客户端连接会话，支持分布式会话存储和负载均衡
 * 
 * 功能说明：
 * - 会话创建和销毁
 * - 会话状态管理
 * - 分布式会话存储（Redis）
 * - 连接数统计和监控
 * - 会话超时清理
 * 
 * 技术选型：Spring + Redis + Netty Channel
 * 
 * @author lx
 */
@Service
public class SessionManager {
    
    private static final Logger logger = GameLogManager.getLogger(SessionManager.class);
    
    /**
     * 本地会话缓存，用于快速访问
     */
    private final ConcurrentHashMap<String, GameSession> localSessions = new ConcurrentHashMap<>();
    
    /**
     * 连接统计
     */
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String SESSION_KEY_PREFIX = "gateway:session:";
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);
    
    /**
     * 创建新会话
     * 
     * @param sessionId 会话ID
     * @param channel   Netty通道
     * @param userId    用户ID（可选）
     * @return 创建的会话对象
     */
    public GameSession createSession(String sessionId, Channel channel, Long userId) {
        GameSession session = new GameSession(sessionId, channel, userId);
        
        // 存储到本地缓存
        localSessions.put(sessionId, session);
        
        // 存储到Redis（分布式会话）
        String redisKey = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(redisKey, session.toRedisData(), SESSION_TIMEOUT);
        
        // 更新统计信息
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        
        logger.info("创建会话: sessionId={}, userId={}, 当前活跃连接数: {}", 
                   sessionId, userId, activeConnections.get());
        
        return session;
    }
    
    /**
     * 获取会话
     * 
     * @param sessionId 会话ID
     * @return 会话对象，不存在则返回null
     */
    public GameSession getSession(String sessionId) {
        // 先从本地缓存获取
        GameSession session = localSessions.get(sessionId);
        if (session != null && session.isActive()) {
            return session;
        }
        
        // 从Redis获取（处理跨网关的会话）
        String redisKey = SESSION_KEY_PREFIX + sessionId;
        Object sessionData = redisTemplate.opsForValue().get(redisKey);
        if (sessionData != null) {
            // 重新构建会话对象（注意：Channel信息会丢失，需要特殊处理）
            logger.debug("从Redis恢复会话: sessionId={}", sessionId);
        }
        
        return session;
    }
    
    /**
     * 移除会话
     * 
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        GameSession session = localSessions.remove(sessionId);
        if (session != null) {
            // 从Redis删除
            String redisKey = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.delete(redisKey);
            
            // 更新统计信息
            activeConnections.decrementAndGet();
            
            logger.info("移除会话: sessionId={}, userId={}, 当前活跃连接数: {}", 
                       sessionId, session.getUserId(), activeConnections.get());
        }
    }
    
    /**
     * 更新会话最后活跃时间
     * 
     * @param sessionId 会话ID
     */
    public void updateSessionActivity(String sessionId) {
        GameSession session = localSessions.get(sessionId);
        if (session != null) {
            session.updateLastActiveTime();
            
            // 延长Redis中的过期时间
            String redisKey = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.expire(redisKey, SESSION_TIMEOUT);
        }
    }
    
    /**
     * 获取当前活跃连接数
     * 
     * @return 活跃连接数
     */
    public long getActiveConnectionCount() {
        return activeConnections.get();
    }
    
    /**
     * 获取总连接数
     * 
     * @return 总连接数
     */
    public long getTotalConnectionCount() {
        return totalConnections.get();
    }
    
    /**
     * 清理过期会话
     * 定期调用此方法清理长时间不活跃的会话
     */
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        long timeoutMs = SESSION_TIMEOUT.toMillis();
        
        localSessions.entrySet().removeIf(entry -> {
            GameSession session = entry.getValue();
            if (currentTime - session.getLastActiveTime() > timeoutMs) {
                logger.info("清理过期会话: sessionId={}, userId={}", 
                           session.getSessionId(), session.getUserId());
                
                // 从Redis删除
                String redisKey = SESSION_KEY_PREFIX + session.getSessionId();
                redisTemplate.delete(redisKey);
                
                // 更新统计信息
                activeConnections.decrementAndGet();
                
                return true;
            }
            return false;
        });
    }
    
    /**
     * 游戏会话对象
     */
    public static class GameSession {
        private final String sessionId;
        private final Channel channel;
        private final Long userId;
        private final long createTime;
        private volatile long lastActiveTime;
        
        public GameSession(String sessionId, Channel channel, Long userId) {
            this.sessionId = sessionId;
            this.channel = channel;
            this.userId = userId;
            this.createTime = System.currentTimeMillis();
            this.lastActiveTime = createTime;
        }
        
        public String getSessionId() { return sessionId; }
        public Channel getChannel() { return channel; }
        public Long getUserId() { return userId; }
        public long getCreateTime() { return createTime; }
        public long getLastActiveTime() { return lastActiveTime; }
        
        public void updateLastActiveTime() {
            this.lastActiveTime = System.currentTimeMillis();
        }
        
        public boolean isActive() {
            return channel != null && channel.isActive();
        }
        
        /**
         * 转换为Redis存储格式
         * 注意：不存储Channel对象，因为它不能序列化
         */
        public String toRedisData() {
            return String.format("{\"sessionId\":\"%s\",\"userId\":%d,\"createTime\":%d,\"lastActiveTime\":%d}",
                    sessionId, userId != null ? userId : 0, createTime, lastActiveTime);
        }
    }
}