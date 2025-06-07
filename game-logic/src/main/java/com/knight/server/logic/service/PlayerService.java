package com.knight.server.logic.service;

import com.knight.server.common.log.GameLogManager;
import com.knight.server.common.utils.SnowflakeIdGenerator;
import com.knight.server.logic.model.Player;
import com.knight.server.logic.repository.PlayerRepository;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 玩家服务类
 * 处理玩家相关的核心业务逻辑，包括登录、属性管理、数据缓存等
 * 
 * 功能说明：
 * - 玩家登录和登出处理
 * - 玩家数据的增删改查
 * - 玩家属性计算和升级
 * - 在线玩家管理和统计
 * - 多级缓存（内存+Redis）
 * 
 * 技术选型：Spring Data MongoDB + Redis + 本地缓存
 * 
 * @author lx
 */
@Service
public class PlayerService {
    
    private static final Logger logger = GameLogManager.getLogger(PlayerService.class);
    
    @Autowired
    private PlayerRepository playerRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private SnowflakeIdGenerator idGenerator;
    
    /**
     * 在线玩家本地缓存，用于快速访问
     */
    private final ConcurrentHashMap<Long, Player> onlinePlayersCache = new ConcurrentHashMap<>();
    
    /**
     * 在线玩家统计
     */
    private final AtomicLong onlinePlayerCount = new AtomicLong(0);
    private final AtomicLong totalLoginCount = new AtomicLong(0);
    
    private static final String PLAYER_CACHE_PREFIX = "logic:player:";
    private static final Duration PLAYER_CACHE_TIMEOUT = Duration.ofMinutes(30);
    
    /**
     * 玩家登录处理
     * 验证玩家身份，加载玩家数据，更新在线状态
     * 
     * @param accountId 账号ID
     * @param token     登录令牌
     * @return 登录成功的玩家对象，失败返回null
     */
    public Player handlePlayerLogin(String accountId, String token) {
        logger.info("玩家登录请求: accountId={}", accountId);
        
        try {
            // 1. 验证token（这里简化处理，实际应该调用认证服务）
            if (!validateToken(accountId, token)) {
                logger.warn("登录失败，token验证失败: accountId={}", accountId);
                return null;
            }
            
            // 2. 查找或创建玩家
            Player player = findOrCreatePlayer(accountId);
            if (player == null) {
                logger.error("登录失败，玩家数据加载失败: accountId={}", accountId);
                return null;
            }
            
            // 3. 更新登录状态
            updatePlayerLoginStatus(player);
            
            // 4. 缓存玩家数据
            cachePlayerData(player);
            
            // 5. 更新统计信息
            onlinePlayerCount.incrementAndGet();
            totalLoginCount.incrementAndGet();
            
            logger.info("玩家登录成功: playerId={}, nickname={}, 当前在线人数: {}", 
                       player.getPlayerId(), player.getNickname(), onlinePlayerCount.get());
            
            return player;
            
        } catch (Exception e) {
            logger.error("玩家登录异常: accountId={}", accountId, e);
            return null;
        }
    }
    
    /**
     * 玩家登出处理
     * 更新玩家状态，保存数据，清理缓存
     * 
     * @param playerId 玩家ID
     */
    public void handlePlayerLogout(Long playerId) {
        logger.info("玩家登出请求: playerId={}", playerId);
        
        try {
            Player player = onlinePlayersCache.get(playerId);
            if (player == null) {
                player = getPlayerById(playerId);
            }
            
            if (player != null) {
                // 更新登出状态
                updatePlayerLogoutStatus(player);
                
                // 保存数据到数据库
                savePlayerData(player);
                
                // 清理缓存
                onlinePlayersCache.remove(playerId);
                clearPlayerCache(playerId);
                
                // 更新统计信息
                onlinePlayerCount.decrementAndGet();
                
                logger.info("玩家登出成功: playerId={}, nickname={}, 当前在线人数: {}", 
                           player.getPlayerId(), player.getNickname(), onlinePlayerCount.get());
            }
            
        } catch (Exception e) {
            logger.error("玩家登出异常: playerId={}", playerId, e);
        }
    }
    
    /**
     * 根据玩家ID获取玩家信息
     * 优先从缓存获取，缓存未命中时从数据库加载
     * 
     * @param playerId 玩家ID
     * @return 玩家对象，不存在返回null
     */
    public Player getPlayerById(Long playerId) {
        // 1. 从在线缓存获取
        Player player = onlinePlayersCache.get(playerId);
        if (player != null) {
            return player;
        }
        
        // 2. 从Redis缓存获取
        player = getPlayerFromRedisCache(playerId);
        if (player != null) {
            return player;
        }
        
        // 3. 从数据库获取
        Optional<Player> playerOptional = playerRepository.findById(playerId);
        if (playerOptional.isPresent()) {
            player = playerOptional.get();
            // 缓存到Redis
            cachePlayerToRedis(player);
            return player;
        }
        
        return null;
    }
    
    /**
     * 根据昵称查找玩家
     * 
     * @param nickname 玩家昵称
     * @return 玩家对象，不存在返回null
     */
    public Player getPlayerByNickname(String nickname) {
        Optional<Player> playerOptional = playerRepository.findByNickname(nickname);
        return playerOptional.orElse(null);
    }
    
    /**
     * 保存玩家数据
     * 同时更新数据库和缓存
     * 
     * @param player 玩家对象
     */
    public void savePlayerData(Player player) {
        try {
            // 保存到数据库
            playerRepository.save(player);
            
            // 更新缓存
            cachePlayerData(player);
            
            logger.debug("玩家数据保存成功: playerId={}", player.getPlayerId());
            
        } catch (Exception e) {
            logger.error("玩家数据保存失败: playerId={}", player.getPlayerId(), e);
        }
    }
    
    /**
     * 获取当前在线玩家数量
     * 
     * @return 在线玩家数量
     */
    public long getOnlinePlayerCount() {
        return onlinePlayerCount.get();
    }
    
    /**
     * 获取总登录次数
     * 
     * @return 总登录次数
     */
    public long getTotalLoginCount() {
        return totalLoginCount.get();
    }
    
    /**
     * 获取在线玩家列表
     * 
     * @return 在线玩家列表
     */
    public List<Player> getOnlinePlayers() {
        return List.copyOf(onlinePlayersCache.values());
    }
    
    /**
     * 验证登录token
     * 
     * @param accountId 账号ID
     * @param token     令牌
     * @return 验证结果
     */
    private boolean validateToken(String accountId, String token) {
        // 这里应该调用认证服务验证token
        // 为了演示，简单检查token格式
        return token != null && token.length() > 10;
    }
    
    /**
     * 查找或创建玩家
     * 
     * @param accountId 账号ID
     * @return 玩家对象
     */
    private Player findOrCreatePlayer(String accountId) {
        // 先尝试根据accountId查找现有玩家
        Optional<Player> existingPlayer = playerRepository.findByAccountId(accountId);
        if (existingPlayer.isPresent()) {
            return existingPlayer.get();
        }
        
        // 创建新玩家
        Long playerId = idGenerator.nextId();
        String nickname = "Player_" + playerId; // 默认昵称
        
        Player newPlayer = new Player(playerId, nickname, accountId);
        
        // 保存到数据库
        playerRepository.save(newPlayer);
        
        logger.info("创建新玩家: playerId={}, nickname={}, accountId={}", 
                   playerId, nickname, accountId);
        
        return newPlayer;
    }
    
    /**
     * 更新玩家登录状态
     * 
     * @param player 玩家对象
     */
    private void updatePlayerLoginStatus(Player player) {
        LocalDateTime now = LocalDateTime.now();
        player.setLastLoginTime(now);
        
        Player.PlayerStatus status = player.getStatus();
        status.setOnline(true);
        status.setOnlineStartTime(now);
        status.setServerNode(getServerNodeId());
    }
    
    /**
     * 更新玩家登出状态
     * 
     * @param player 玩家对象
     */
    private void updatePlayerLogoutStatus(Player player) {
        LocalDateTime now = LocalDateTime.now();
        player.setLastLogoutTime(now);
        
        Player.PlayerStatus status = player.getStatus();
        status.setOnline(false);
        
        // 计算本次在线时长
        if (status.getOnlineStartTime() != null) {
            Duration onlineSession = Duration.between(status.getOnlineStartTime(), now);
            player.setTotalOnlineTime(player.getTotalOnlineTime() + onlineSession.getSeconds());
        }
        
        status.setOnlineStartTime(null);
    }
    
    /**
     * 缓存玩家数据
     * 
     * @param player 玩家对象
     */
    private void cachePlayerData(Player player) {
        // 在线缓存
        onlinePlayersCache.put(player.getPlayerId(), player);
        
        // Redis缓存
        cachePlayerToRedis(player);
    }
    
    /**
     * 缓存玩家到Redis
     * 
     * @param player 玩家对象
     */
    private void cachePlayerToRedis(Player player) {
        String cacheKey = PLAYER_CACHE_PREFIX + player.getPlayerId();
        redisTemplate.opsForValue().set(cacheKey, player, PLAYER_CACHE_TIMEOUT);
    }
    
    /**
     * 从Redis获取玩家缓存
     * 
     * @param playerId 玩家ID
     * @return 玩家对象，不存在返回null
     */
    private Player getPlayerFromRedisCache(Long playerId) {
        String cacheKey = PLAYER_CACHE_PREFIX + playerId;
        Object cachedPlayer = redisTemplate.opsForValue().get(cacheKey);
        return cachedPlayer instanceof Player ? (Player) cachedPlayer : null;
    }
    
    /**
     * 清理玩家缓存
     * 
     * @param playerId 玩家ID
     */
    private void clearPlayerCache(Long playerId) {
        String cacheKey = PLAYER_CACHE_PREFIX + playerId;
        redisTemplate.delete(cacheKey);
    }
    
    /**
     * 获取服务器节点ID
     * 
     * @return 节点ID
     */
    private String getServerNodeId() {
        // 这里应该返回当前服务器节点的唯一标识
        return "logic-node-" + System.getProperty("server.port", "9001");
    }
}