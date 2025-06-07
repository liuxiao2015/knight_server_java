package com.knight.server.service.gateway;

import com.knight.server.common.log.LoggerManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 连接注册表
 * 管理客户端连接的认证状态和用户信息
 * 技术选型：ConcurrentHashMap + 连接状态管理
 * 
 * @author lx
 */
public class ConnectionRegistry {
    
    private static final Logger logger = LoggerManager.getLogger(ConnectionRegistry.class);
    
    // 连接认证状态 connectionId -> AuthInfo
    private final ConcurrentMap<String, AuthInfo> authenticatedConnections = new ConcurrentHashMap<>();
    
    // 用户连接映射 userId -> connectionId
    private final ConcurrentMap<String, String> userConnections = new ConcurrentHashMap<>();
    
    /**
     * 认证连接
     * 
     * @param connectionId 连接ID
     * @param userId 用户ID
     * @param token JWT Token
     */
    public void authenticateConnection(String connectionId, String userId, String token) {
        AuthInfo authInfo = new AuthInfo(userId, token, System.currentTimeMillis());
        authenticatedConnections.put(connectionId, authInfo);
        
        // 检查是否已有其他连接，如果有则踢掉（单设备登录）
        String existingConnection = userConnections.put(userId, connectionId);
        if (existingConnection != null && !existingConnection.equals(connectionId)) {
            logger.info("用户{}重复登录，踢掉旧连接: {}", userId, existingConnection);
            authenticatedConnections.remove(existingConnection);
        }
        
        logger.debug("连接认证成功: {} -> {}", connectionId, userId);
    }
    
    /**
     * 取消认证
     * 
     * @param connectionId 连接ID
     */
    public void unauthenticateConnection(String connectionId) {
        AuthInfo authInfo = authenticatedConnections.remove(connectionId);
        if (authInfo != null) {
            userConnections.remove(authInfo.getUserId());
            logger.debug("连接认证取消: {} -> {}", connectionId, authInfo.getUserId());
        }
    }
    
    /**
     * 检查连接是否已认证
     * 
     * @param connectionId 连接ID
     * @return 是否已认证
     */
    public boolean isAuthenticated(String connectionId) {
        return authenticatedConnections.containsKey(connectionId);
    }
    
    /**
     * 获取连接的认证信息
     * 
     * @param connectionId 连接ID
     * @return 认证信息
     */
    public AuthInfo getAuthInfo(String connectionId) {
        return authenticatedConnections.get(connectionId);
    }
    
    /**
     * 根据用户ID获取连接ID
     * 
     * @param userId 用户ID
     * @return 连接ID
     */
    public String getConnectionByUserId(String userId) {
        return userConnections.get(userId);
    }
    
    /**
     * 获取已认证连接数
     * 
     * @return 已认证连接数
     */
    public int getAuthenticatedCount() {
        return authenticatedConnections.size();
    }
    
    /**
     * 清理所有连接
     */
    public void clear() {
        authenticatedConnections.clear();
        userConnections.clear();
        logger.info("连接注册表已清理");
    }
    
    /**
     * 认证信息
     */
    public static class AuthInfo {
        private final String userId;
        private final String token;
        private final long authenticatedTime;
        
        public AuthInfo(String userId, String token, long authenticatedTime) {
            this.userId = userId;
            this.token = token;
            this.authenticatedTime = authenticatedTime;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getToken() {
            return token;
        }
        
        public long getAuthenticatedTime() {
            return authenticatedTime;
        }
    }
}