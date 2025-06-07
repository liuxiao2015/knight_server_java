package com.knight.server.frame.network;

import com.knight.server.common.log.LoggerManager;
import com.knight.server.common.utils.IdGenerator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接管理器
 * 管理所有活跃的网络连接，支持消息广播和单点发送
 * 技术选型：ConcurrentHashMap + Netty Channel管理
 * 
 * @author lx
 */
public class ConnectionManager {
    
    private static final Logger logger = LoggerManager.getLogger(ConnectionManager.class);
    
    // 连接映射 connectionId -> Channel
    private final ConcurrentMap<String, Channel> connections = new ConcurrentHashMap<>();
    
    // 反向映射 Channel -> connectionId
    private final ConcurrentMap<Channel, String> channelToId = new ConcurrentHashMap<>();
    
    // ID生成器
    private final IdGenerator idGenerator = IdGenerator.getInstance();
    
    // 消息发送统计
    private final AtomicLong messageSent = new AtomicLong(0);
    private final AtomicLong messageFailures = new AtomicLong(0);
    
    /**
     * 添加新连接
     * 
     * @param channel 网络通道
     * @return 连接ID
     */
    public String addConnection(Channel channel) {
        String connectionId = "conn_" + idGenerator.nextStringId();
        connections.put(connectionId, channel);
        channelToId.put(channel, connectionId);
        
        logger.debug("添加连接: {} -> {}", connectionId, channel.remoteAddress());
        return connectionId;
    }
    
    /**
     * 移除连接
     * 
     * @param channel 网络通道
     */
    public void removeConnection(Channel channel) {
        String connectionId = channelToId.remove(channel);
        if (connectionId != null) {
            connections.remove(connectionId);
            logger.debug("移除连接: {} -> {}", connectionId, channel.remoteAddress());
        }
    }
    
    /**
     * 根据通道获取连接ID
     * 
     * @param channel 网络通道
     * @return 连接ID
     */
    public String getConnectionId(Channel channel) {
        return channelToId.get(channel);
    }
    
    /**
     * 根据连接ID获取通道
     * 
     * @param connectionId 连接ID
     * @return 网络通道
     */
    public Channel getChannel(String connectionId) {
        return connections.get(connectionId);
    }
    
    /**
     * 发送消息给指定连接
     * 
     * @param connectionId 连接ID
     * @param message 消息
     * @return 是否成功发送
     */
    public boolean sendMessage(String connectionId, Object message) {
        Channel channel = connections.get(connectionId);
        if (channel != null && channel.isActive()) {
            try {
                ChannelFuture future = channel.writeAndFlush(message);
                future.addListener(f -> {
                    if (f.isSuccess()) {
                        messageSent.incrementAndGet();
                    } else {
                        messageFailures.incrementAndGet();
                        logger.warn("发送消息失败: {}, 原因: {}", connectionId, f.cause().getMessage());
                    }
                });
                return true;
            } catch (Exception e) {
                messageFailures.incrementAndGet();
                logger.error("发送消息异常: {}", connectionId, e);
                return false;
            }
        }
        return false;
    }
    
    /**
     * 广播消息给所有连接
     * 
     * @param message 消息
     * @return 成功发送的连接数
     */
    public int broadcast(Object message) {
        int successCount = 0;
        for (String connectionId : connections.keySet()) {
            if (sendMessage(connectionId, message)) {
                successCount++;
            }
        }
        
        logger.debug("广播消息完成，成功: {}, 总连接: {}", successCount, connections.size());
        return successCount;
    }
    
    /**
     * 广播消息给指定连接列表
     * 
     * @param connectionIds 连接ID列表
     * @param message 消息
     * @return 成功发送的连接数
     */
    public int broadcast(String[] connectionIds, Object message) {
        int successCount = 0;
        for (String connectionId : connectionIds) {
            if (sendMessage(connectionId, message)) {
                successCount++;
            }
        }
        return successCount;
    }
    
    /**
     * 关闭指定连接
     * 
     * @param connectionId 连接ID
     */
    public void closeConnection(String connectionId) {
        Channel channel = connections.get(connectionId);
        if (channel != null) {
            channel.close();
            logger.debug("主动关闭连接: {}", connectionId);
        }
    }
    
    /**
     * 关闭所有连接
     */
    public void closeAll() {
        logger.info("关闭所有连接，总数: {}", connections.size());
        
        for (Channel channel : connections.values()) {
            try {
                if (channel.isActive()) {
                    channel.close().sync();
                }
            } catch (Exception e) {
                logger.warn("关闭连接异常", e);
            }
        }
        
        connections.clear();
        channelToId.clear();
        logger.info("所有连接已关闭");
    }
    
    /**
     * 获取活跃连接数
     * 
     * @return 活跃连接数
     */
    public int getActiveConnections() {
        return connections.size();
    }
    
    /**
     * 获取所有连接ID
     * 
     * @return 连接ID数组
     */
    public String[] getAllConnectionIds() {
        return connections.keySet().toArray(new String[0]);
    }
    
    /**
     * 检查连接是否存在且活跃
     * 
     * @param connectionId 连接ID
     * @return 是否活跃
     */
    public boolean isActive(String connectionId) {
        Channel channel = connections.get(connectionId);
        return channel != null && channel.isActive();
    }
    
    /**
     * 获取连接统计信息
     * 
     * @return 统计信息
     */
    public ConnectionStats getStats() {
        return new ConnectionStats(
            connections.size(),
            messageSent.get(),
            messageFailures.get()
        );
    }
    
    /**
     * 连接统计信息记录
     */
    public record ConnectionStats(
        int activeConnections,
        long messageSent,
        long messageFailures
    ) {}
}