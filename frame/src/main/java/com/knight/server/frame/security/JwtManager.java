package com.knight.server.frame.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.knight.server.common.log.LoggerManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JWT Token认证管理器
 * 提供Token生成、验证和管理功能
 * 技术选型：Auth0 JWT + HMAC256算法 + Token黑名单
 * 
 * @author lx
 */
public class JwtManager {
    
    private static final Logger logger = LoggerManager.getLogger(JwtManager.class);
    
    // JWT密钥
    private static final String SECRET_KEY = "knight_server_jwt_secret_key_2024";
    
    // 算法实例
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);
    
    // JWT验证器
    private static final JWTVerifier VERIFIER = JWT.require(ALGORITHM).build();
    
    // Token黑名单（用于登出和强制失效）
    private static final ConcurrentMap<String, Long> TOKEN_BLACKLIST = new ConcurrentHashMap<>();
    
    // 默认过期时间（7天）
    private static final long DEFAULT_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;
    
    // 刷新Token过期时间（30天）
    private static final long REFRESH_EXPIRE_TIME = 30 * 24 * 60 * 60 * 1000L;
    
    /**
     * 生成访问Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param roles 用户角色
     * @return JWT Token
     */
    public static String generateAccessToken(String userId, String username, String[] roles) {
        return generateToken(userId, username, roles, DEFAULT_EXPIRE_TIME, "access");
    }
    
    /**
     * 生成刷新Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT Token
     */
    public static String generateRefreshToken(String userId, String username) {
        return generateToken(userId, username, new String[0], REFRESH_EXPIRE_TIME, "refresh");
    }
    
    /**
     * 生成Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param roles 用户角色
     * @param expireTime 过期时间（毫秒）
     * @param tokenType Token类型
     * @return JWT Token
     */
    private static String generateToken(String userId, String username, String[] roles, long expireTime, String tokenType) {
        try {
            Date now = new Date();
            Date expireDate = new Date(now.getTime() + expireTime);
            
            return JWT.create()
                    .withIssuer("knight-server")
                    .withSubject(userId)
                    .withClaim("username", username)
                    .withClaim("roles", String.join(",", roles))
                    .withClaim("type", tokenType)
                    .withIssuedAt(now)
                    .withExpiresAt(expireDate)
                    .sign(ALGORITHM);
                    
        } catch (Exception e) {
            logger.error("生成JWT Token失败", e);
            throw new RuntimeException("Token generation failed", e);
        }
    }
    
    /**
     * 验证Token
     * 
     * @param token JWT Token
     * @return 验证结果
     */
    public static TokenValidation validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return TokenValidation.invalid("Token为空");
        }
        
        // 检查黑名单
        if (TOKEN_BLACKLIST.containsKey(token)) {
            return TokenValidation.invalid("Token已失效");
        }
        
        try {
            DecodedJWT decodedJWT = VERIFIER.verify(token);
            
            // 检查是否过期
            if (decodedJWT.getExpiresAt().before(new Date())) {
                return TokenValidation.invalid("Token已过期");
            }
            
            // 提取信息
            String userId = decodedJWT.getSubject();
            String username = decodedJWT.getClaim("username").asString();
            String rolesStr = decodedJWT.getClaim("roles").asString();
            String tokenType = decodedJWT.getClaim("type").asString();
            String[] roles = rolesStr != null ? rolesStr.split(",") : new String[0];
            
            return TokenValidation.valid(new TokenInfo(userId, username, roles, tokenType, decodedJWT.getExpiresAt()));
            
        } catch (JWTVerificationException e) {
            logger.warn("Token验证失败: {}", e.getMessage());
            return TokenValidation.invalid("Token验证失败: " + e.getMessage());
        }
    }
    
    /**
     * 刷新Token
     * 
     * @param refreshToken 刷新Token
     * @return 新的访问Token，如果刷新失败返回null
     */
    public static String refreshAccessToken(String refreshToken) {
        TokenValidation validation = validateToken(refreshToken);
        if (!validation.isValid()) {
            return null;
        }
        
        TokenInfo tokenInfo = validation.getTokenInfo();
        if (!"refresh".equals(tokenInfo.getTokenType())) {
            return null;
        }
        
        // 生成新的访问Token
        return generateAccessToken(tokenInfo.getUserId(), tokenInfo.getUsername(), tokenInfo.getRoles());
    }
    
    /**
     * 撤销Token（加入黑名单）
     * 
     * @param token JWT Token
     */
    public static void revokeToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            TOKEN_BLACKLIST.put(token, System.currentTimeMillis());
            logger.info("Token已撤销");
        }
    }
    
    /**
     * 清理过期的黑名单Token
     */
    public static void cleanupBlacklist() {
        long now = System.currentTimeMillis();
        TOKEN_BLACKLIST.entrySet().removeIf(entry -> {
            // 如果黑名单中的Token已经过期很久（超过最长Token有效期），则可以清理
            return (now - entry.getValue()) > REFRESH_EXPIRE_TIME;
        });
        
        logger.debug("清理Token黑名单，当前大小: {}", TOKEN_BLACKLIST.size());
    }
    
    /**
     * 获取Token信息（不验证有效性）
     * 
     * @param token JWT Token
     * @return Token信息
     */
    public static TokenInfo parseToken(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            String userId = decodedJWT.getSubject();
            String username = decodedJWT.getClaim("username").asString();
            String rolesStr = decodedJWT.getClaim("roles").asString();
            String tokenType = decodedJWT.getClaim("type").asString();
            String[] roles = rolesStr != null ? rolesStr.split(",") : new String[0];
            
            return new TokenInfo(userId, username, roles, tokenType, decodedJWT.getExpiresAt());
        } catch (Exception e) {
            logger.warn("解析Token失败", e);
            return null;
        }
    }
    
    /**
     * Token验证结果
     */
    public static class TokenValidation {
        private final boolean valid;
        private final String errorMessage;
        private final TokenInfo tokenInfo;
        
        private TokenValidation(boolean valid, String errorMessage, TokenInfo tokenInfo) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.tokenInfo = tokenInfo;
        }
        
        public static TokenValidation valid(TokenInfo tokenInfo) {
            return new TokenValidation(true, null, tokenInfo);
        }
        
        public static TokenValidation invalid(String errorMessage) {
            return new TokenValidation(false, errorMessage, null);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public TokenInfo getTokenInfo() {
            return tokenInfo;
        }
    }
    
    /**
     * Token信息
     */
    public static class TokenInfo {
        private final String userId;
        private final String username;
        private final String[] roles;
        private final String tokenType;
        private final Date expireTime;
        
        public TokenInfo(String userId, String username, String[] roles, String tokenType, Date expireTime) {
            this.userId = userId;
            this.username = username;
            this.roles = roles;
            this.tokenType = tokenType;
            this.expireTime = expireTime;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String[] getRoles() {
            return roles;
        }
        
        public String getTokenType() {
            return tokenType;
        }
        
        public Date getExpireTime() {
            return expireTime;
        }
        
        public boolean hasRole(String role) {
            if (roles == null) {
                return false;
            }
            for (String r : roles) {
                if (role.equals(r)) {
                    return true;
                }
            }
            return false;
        }
    }
}