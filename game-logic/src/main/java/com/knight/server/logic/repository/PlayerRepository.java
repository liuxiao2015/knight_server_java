package com.knight.server.logic.repository;

import com.knight.server.logic.model.Player;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 玩家数据访问层
 * 提供玩家数据的数据库操作接口
 * 
 * 功能说明：
 * - 基础CRUD操作
 * - 自定义查询方法
 * - 统计查询方法
 * - 索引优化查询
 * 
 * 技术选型：Spring Data MongoDB Repository
 * 
 * @author lx
 */
@Repository
public interface PlayerRepository extends MongoRepository<Player, Long> {
    
    /**
     * 根据账号ID查找玩家
     * 用于登录时查找现有玩家账号
     * 
     * @param accountId 账号ID
     * @return 玩家对象
     */
    Optional<Player> findByAccountId(String accountId);
    
    /**
     * 根据昵称查找玩家
     * 昵称具有唯一索引，用于昵称查重和搜索
     * 
     * @param nickname 玩家昵称
     * @return 玩家对象
     */
    Optional<Player> findByNickname(String nickname);
    
    /**
     * 检查昵称是否存在
     * 用于注册时的昵称重复检查
     * 
     * @param nickname 昵称
     * @return 是否存在
     */
    boolean existsByNickname(String nickname);
    
    /**
     * 根据等级范围查找玩家
     * 用于等级排行榜和匹配系统
     * 
     * @param minLevel 最小等级
     * @param maxLevel 最大等级
     * @return 玩家列表
     */
    List<Player> findByLevelBetween(Integer minLevel, Integer maxLevel);
    
    /**
     * 根据等级降序查找前N个玩家
     * 用于等级排行榜
     * 
     * @return 玩家列表（按等级降序）
     */
    @Query(value = "{}", sort = "{ 'level': -1, 'experience': -1 }")
    List<Player> findTopPlayersByLevel();
    
    /**
     * 查找在线玩家
     * 根据玩家状态中的在线标记查找
     * 
     * @return 在线玩家列表
     */
    @Query("{ 'status.online': true }")
    List<Player> findOnlinePlayers();
    
    /**
     * 查找指定时间后登录的玩家
     * 用于活跃玩家统计
     * 
     * @param dateTime 时间点
     * @return 玩家列表
     */
    List<Player> findByLastLoginTimeAfter(LocalDateTime dateTime);
    
    /**
     * 查找指定时间内创建的新玩家
     * 用于新用户统计
     * 
     * @param dateTime 时间点
     * @return 新玩家列表
     */
    List<Player> findByCreateTimeAfter(LocalDateTime dateTime);
    
    /**
     * 统计在线玩家数量
     * 
     * @return 在线玩家数量
     */
    @Query(value = "{ 'status.online': true }", count = true)
    long countOnlinePlayers();
    
    /**
     * 统计指定等级以上的玩家数量
     * 
     * @param level 等级阈值
     * @return 玩家数量
     */
    long countByLevelGreaterThanEqual(Integer level);
    
    /**
     * 统计指定时间后登录的活跃玩家数量
     * 
     * @param dateTime 时间点
     * @return 活跃玩家数量
     */
    long countByLastLoginTimeAfter(LocalDateTime dateTime);
    
    /**
     * 查找金币数量前N的富豪玩家
     * 用于财富排行榜
     * 
     * @return 玩家列表（按金币降序）
     */
    @Query(value = "{}", sort = "{ 'coins': -1 }")
    List<Player> findTopPlayersByCoins();
    
    /**
     * 根据服务器节点查找玩家
     * 用于服务器负载均衡和玩家迁移
     * 
     * @param serverNode 服务器节点ID
     * @return 玩家列表
     */
    @Query("{ 'status.serverNode': ?0, 'status.online': true }")
    List<Player> findByServerNode(String serverNode);
    
    /**
     * 删除长期未登录的僵尸账号
     * 清理超过指定时间未登录的账号（软删除）
     * 
     * @param dateTime 时间阈值
     * @return 删除的账号数量
     */
    long deleteByLastLoginTimeBefore(LocalDateTime dateTime);
    
    /**
     * 自定义查询：根据战力排序
     * 通过聚合查询计算战力并排序
     * 
     * @return 按战力排序的玩家列表
     */
    @Query(value = "{ $expr: { $gt: [ { $add: [ " +
           "{ $multiply: ['$attributes.hp', 0.1] }, " +
           "{ $multiply: ['$attributes.attack', 2] }, " +
           "{ $multiply: ['$attributes.defense', 1.5] }, " +
           "{ $multiply: ['$attributes.critical', 3] }, " +
           "{ $multiply: ['$level', 100] } " +
           "] }, 0 ] } }", 
           sort = "{ 'level': -1, 'attributes.attack': -1 }")
    List<Player> findTopPlayersByCombatPower();
}