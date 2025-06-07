package com.knight.server.logic.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 玩家数据模型
 * 游戏中玩家的核心数据结构，包含所有玩家相关信息
 * 
 * 功能说明：
 * - 玩家基础信息（ID、昵称、等级等）
 * - 玩家属性（战力、血量、攻击力等）
 * - 游戏进度（经验、金币、物品等）
 * - 时间信息（注册、登录、在线时长等）
 * 
 * 技术选型：Spring Data MongoDB + 索引优化
 * 
 * @author lx
 */
@Document(collection = "players")
public class Player {
    
    /**
     * 玩家唯一ID
     */
    @Id
    private Long playerId;
    
    /**
     * 玩家昵称，需要建立唯一索引
     */
    @Indexed(unique = true)
    private String nickname;
    
    /**
     * 账号ID，关联登录账号
     */
    @Indexed
    private String accountId;
    
    /**
     * 玩家等级
     */
    private Integer level = 1;
    
    /**
     * 当前经验值
     */
    private Long experience = 0L;
    
    /**
     * 金币数量
     */
    private Long coins = 10000L;
    
    /**
     * 钻石数量（充值货币）
     */
    private Long diamonds = 0L;
    
    /**
     * 玩家属性
     */
    private PlayerAttributes attributes;
    
    /**
     * 玩家状态
     */
    private PlayerStatus status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 最后登录时间
     */
    @Indexed
    private LocalDateTime lastLoginTime;
    
    /**
     * 最后登出时间
     */
    private LocalDateTime lastLogoutTime;
    
    /**
     * 总在线时长（秒）
     */
    private Long totalOnlineTime = 0L;
    
    /**
     * 扩展属性，用于存储其他游戏数据
     */
    private Map<String, Object> extendedProperties = new HashMap<>();
    
    // 构造函数
    public Player() {
        this.attributes = new PlayerAttributes();
        this.status = new PlayerStatus();
        this.createTime = LocalDateTime.now();
        this.lastLoginTime = LocalDateTime.now();
    }
    
    public Player(Long playerId, String nickname, String accountId) {
        this();
        this.playerId = playerId;
        this.nickname = nickname;
        this.accountId = accountId;
    }
    
    /**
     * 计算玩家战力
     * 根据玩家属性计算综合战斗力
     * 
     * @return 战斗力数值
     */
    public Long calculateCombatPower() {
        if (attributes == null) {
            return 0L;
        }
        
        // 战力计算公式：生命值*0.1 + 攻击力*2 + 防御力*1.5 + 暴击*3 + 等级*100
        long power = (long) (
            attributes.getHp() * 0.1 +
            attributes.getAttack() * 2 +
            attributes.getDefense() * 1.5 +
            attributes.getCritical() * 3 +
            level * 100
        );
        
        return power;
    }
    
    /**
     * 升级检查和处理
     * 检查经验值是否足够升级，如果足够则自动升级
     * 
     * @return 是否升级成功
     */
    public boolean checkAndLevelUp() {
        long requiredExp = calculateRequiredExp(level);
        if (experience >= requiredExp && level < 200) { // 最高等级200
            experience -= requiredExp;
            level++;
            
            // 升级时属性增长
            if (attributes != null) {
                attributes.levelUpBonus();
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * 计算升级所需经验
     * 
     * @param currentLevel 当前等级
     * @return 升级所需经验值
     */
    private long calculateRequiredExp(int currentLevel) {
        // 经验需求公式：基础经验 * (等级^1.2)
        return (long) (1000 * Math.pow(currentLevel, 1.2));
    }
    
    /**
     * 添加经验值
     * 
     * @param exp 要增加的经验值
     * @return 是否发生了升级
     */
    public boolean addExperience(long exp) {
        this.experience += exp;
        return checkAndLevelUp();
    }
    
    /**
     * 添加金币
     * 
     * @param amount 金币数量
     * @return 添加后的总金币数
     */
    public long addCoins(long amount) {
        this.coins = Math.max(0, this.coins + amount);
        return this.coins;
    }
    
    /**
     * 添加钻石
     * 
     * @param amount 钻石数量
     * @return 添加后的总钻石数
     */
    public long addDiamonds(long amount) {
        this.diamonds = Math.max(0, this.diamonds + amount);
        return this.diamonds;
    }
    
    // Getters and Setters
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    
    public Long getExperience() { return experience; }
    public void setExperience(Long experience) { this.experience = experience; }
    
    public Long getCoins() { return coins; }
    public void setCoins(Long coins) { this.coins = coins; }
    
    public Long getDiamonds() { return diamonds; }
    public void setDiamonds(Long diamonds) { this.diamonds = diamonds; }
    
    public PlayerAttributes getAttributes() { return attributes; }
    public void setAttributes(PlayerAttributes attributes) { this.attributes = attributes; }
    
    public PlayerStatus getStatus() { return status; }
    public void setStatus(PlayerStatus status) { this.status = status; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    
    public LocalDateTime getLastLogoutTime() { return lastLogoutTime; }
    public void setLastLogoutTime(LocalDateTime lastLogoutTime) { this.lastLogoutTime = lastLogoutTime; }
    
    public Long getTotalOnlineTime() { return totalOnlineTime; }
    public void setTotalOnlineTime(Long totalOnlineTime) { this.totalOnlineTime = totalOnlineTime; }
    
    public Map<String, Object> getExtendedProperties() { return extendedProperties; }
    public void setExtendedProperties(Map<String, Object> extendedProperties) { this.extendedProperties = extendedProperties; }
    
    /**
     * 玩家属性内部类
     */
    public static class PlayerAttributes {
        private Long hp = 1000L;          // 生命值
        private Long attack = 100L;       // 攻击力
        private Long defense = 50L;       // 防御力
        private Long critical = 10L;      // 暴击
        private Long speed = 100L;        // 速度
        private Long accuracy = 90L;      // 命中率
        private Long dodge = 10L;         // 闪避率
        
        /**
         * 升级时的属性奖励
         */
        public void levelUpBonus() {
            hp += 100;
            attack += 10;
            defense += 5;
            critical += 2;
            speed += 3;
        }
        
        // Getters and Setters
        public Long getHp() { return hp; }
        public void setHp(Long hp) { this.hp = hp; }
        
        public Long getAttack() { return attack; }
        public void setAttack(Long attack) { this.attack = attack; }
        
        public Long getDefense() { return defense; }
        public void setDefense(Long defense) { this.defense = defense; }
        
        public Long getCritical() { return critical; }
        public void setCritical(Long critical) { this.critical = critical; }
        
        public Long getSpeed() { return speed; }
        public void setSpeed(Long speed) { this.speed = speed; }
        
        public Long getAccuracy() { return accuracy; }
        public void setAccuracy(Long accuracy) { this.accuracy = accuracy; }
        
        public Long getDodge() { return dodge; }
        public void setDodge(Long dodge) { this.dodge = dodge; }
    }
    
    /**
     * 玩家状态内部类
     */
    public static class PlayerStatus {
        private boolean online = false;              // 是否在线
        private String serverNode;                   // 所在服务器节点
        private LocalDateTime onlineStartTime;       // 本次上线时间
        private String lastKnownLocation;            // 最后已知位置
        
        // Getters and Setters
        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }
        
        public String getServerNode() { return serverNode; }
        public void setServerNode(String serverNode) { this.serverNode = serverNode; }
        
        public LocalDateTime getOnlineStartTime() { return onlineStartTime; }
        public void setOnlineStartTime(LocalDateTime onlineStartTime) { this.onlineStartTime = onlineStartTime; }
        
        public String getLastKnownLocation() { return lastKnownLocation; }
        public void setLastKnownLocation(String lastKnownLocation) { this.lastKnownLocation = lastKnownLocation; }
    }
}