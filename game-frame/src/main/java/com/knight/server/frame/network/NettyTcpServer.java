package com.knight.server.frame.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能网络服务器
 * 基于Netty NIO实现TCP长连接管理，支持100,000+并发连接
 * 
 * 技术选型：Netty 4.x + NIO + 心跳检测 + 消息压缩
 * 
 * @author lx
 */
public class NettyTcpServer {
    
    private static final Logger logger = LogManager.getLogger(NettyTcpServer.class);
    
    // 服务器配置
    private final int port;
    private final int bossThreads;
    private final int workerThreads;
    
    // Netty组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    // 连接管理
    private static final ConcurrentHashMap<String, Channel> connections = new ConcurrentHashMap<>();
    private static final AtomicLong connectionCounter = new AtomicLong(0);
    
    // 心跳配置
    private static final int READER_IDLE_TIME = 60;  // 读空闲时间(秒)
    private static final int WRITER_IDLE_TIME = 30;  // 写空闲时间(秒)
    private static final int ALL_IDLE_TIME = 90;     // 读写空闲时间(秒)
    
    // 消息统计
    private static final AtomicLong receivedMessages = new AtomicLong(0);
    private static final AtomicLong sentMessages = new AtomicLong(0);
    
    public NettyTcpServer(int port, int bossThreads, int workerThreads) {
        this.port = port;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        try {
            // 创建线程组
            bossGroup = new NioEventLoopGroup(bossThreads);
            workerGroup = new NioEventLoopGroup(workerThreads);
            
            // 配置服务器
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.SO_RCVBUF, 64 * 1024)
                    .childOption(ChannelOption.SO_SNDBUF, 64 * 1024)
                    .childHandler(new ServerChannelInitializer());
            
            // 绑定端口并启动
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            logger.info("TCP服务器启动成功，端口: {}, Boss线程: {}, Worker线程: {}", 
                       port, bossThreads, workerThreads);
            
        } catch (Exception e) {
            logger.error("TCP服务器启动失败", e);
            shutdown();
        }
    }
    
    /**
     * 关闭服务器
     */
    public void shutdown() {
        logger.info("开始关闭TCP服务器...");
        
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException e) {
            logger.error("关闭服务器通道异常", e);
        } finally {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
        
        logger.info("TCP服务器已关闭");
    }
    
    /**
     * 向指定连接发送消息
     * 
     * @param channelId 连接ID
     * @param message 消息内容
     */
    public static void sendMessage(String channelId, Object message) {
        Channel channel = connections.get(channelId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
            sentMessages.incrementAndGet();
        } else {
            logger.warn("连接不存在或已断开: {}", channelId);
        }
    }
    
    /**
     * 广播消息到所有连接
     * 
     * @param message 消息内容
     */
    public static void broadcast(Object message) {
        connections.values().parallelStream()
                .filter(Channel::isActive)
                .forEach(channel -> {
                    channel.writeAndFlush(message);
                    sentMessages.incrementAndGet();
                });
    }
    
    /**
     * 获取连接统计信息
     * 
     * @return 统计信息
     */
    public static NetworkStats getStats() {
        return new NetworkStats(
            connections.size(),
            connectionCounter.get(),
            receivedMessages.get(),
            sentMessages.get()
        );
    }
    
    /**
     * 服务器通道初始化器
     */
    private static class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
        
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            
            // 空闲检测处理器
            pipeline.addLast("idleStateHandler", 
                           new IdleStateHandler(READER_IDLE_TIME, WRITER_IDLE_TIME, ALL_IDLE_TIME));
            
            // 长度字段解码器 (解决TCP粘包/拆包问题)
            pipeline.addLast("frameDecoder", 
                           new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
            
            // 长度字段编码器
            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
            
            // 消息编解码器
            pipeline.addLast("messageDecoder", new GameMessageCodec.Decoder());
            pipeline.addLast("messageEncoder", new GameMessageCodec.Encoder());
            
            // 业务处理器
            pipeline.addLast("gameHandler", new GameServerHandler());
        }
    }
    
    /**
     * 游戏服务器处理器
     */
    private static class GameServerHandler extends SimpleChannelInboundHandler<Object> {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Channel channel = ctx.channel();
            String channelId = generateChannelId(channel);
            
            connections.put(channelId, channel);
            connectionCounter.incrementAndGet();
            
            // 存储通道ID到通道属性中
            channel.attr(ChannelAttributes.CHANNEL_ID).set(channelId);
            
            logger.info("新连接建立: {} -> {}", channelId, channel.remoteAddress());
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            Channel channel = ctx.channel();
            String channelId = channel.attr(ChannelAttributes.CHANNEL_ID).get();
            
            if (channelId != null) {
                connections.remove(channelId);
                logger.info("连接断开: {} -> {}", channelId, channel.remoteAddress());
            }
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            receivedMessages.incrementAndGet();
            
            // 处理接收到的消息
            handleMessage(ctx, msg);
        }
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
                io.netty.handler.timeout.IdleStateEvent event = 
                    (io.netty.handler.timeout.IdleStateEvent) evt;
                
                switch (event.state()) {
                    case READER_IDLE:
                        logger.warn("读空闲超时，关闭连接: {}", ctx.channel().remoteAddress());
                        ctx.close();
                        break;
                    case WRITER_IDLE:
                        // 发送心跳包
                        ctx.writeAndFlush(createHeartbeatMessage());
                        break;
                    case ALL_IDLE:
                        logger.warn("读写空闲超时，关闭连接: {}", ctx.channel().remoteAddress());
                        ctx.close();
                        break;
                }
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("连接异常: {}", ctx.channel().remoteAddress(), cause);
            ctx.close();
        }
        
        private void handleMessage(ChannelHandlerContext ctx, Object message) {
            // 这里可以根据消息类型进行分发处理
            logger.debug("收到消息: {} from {}", message, ctx.channel().remoteAddress());
            
            // 示例：回显消息
            ctx.writeAndFlush(message);
        }
        
        private Object createHeartbeatMessage() {
            // 创建心跳消息
            return "HEARTBEAT";
        }
        
        private String generateChannelId(Channel channel) {
            InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
            return String.format("%s:%d-%d", 
                               remoteAddress.getHostString(),
                               remoteAddress.getPort(),
                               System.currentTimeMillis());
        }
    }
    
    /**
     * 通道属性定义
     */
    private static class ChannelAttributes {
        static final io.netty.util.AttributeKey<String> CHANNEL_ID = 
            io.netty.util.AttributeKey.valueOf("channelId");
        static final io.netty.util.AttributeKey<Long> USER_ID = 
            io.netty.util.AttributeKey.valueOf("userId");
        static final io.netty.util.AttributeKey<String> SESSION_ID = 
            io.netty.util.AttributeKey.valueOf("sessionId");
    }
    
    /**
     * 网络统计信息
     */
    public static class NetworkStats {
        private final int activeConnections;
        private final long totalConnections;
        private final long receivedMessages;
        private final long sentMessages;
        
        public NetworkStats(int activeConnections, long totalConnections, 
                          long receivedMessages, long sentMessages) {
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
            this.receivedMessages = receivedMessages;
            this.sentMessages = sentMessages;
        }
        
        public int getActiveConnections() { return activeConnections; }
        public long getTotalConnections() { return totalConnections; }
        public long getReceivedMessages() { return receivedMessages; }
        public long getSentMessages() { return sentMessages; }
        
        @Override
        public String toString() {
            return String.format(
                "NetworkStats{活跃连接=%d, 总连接数=%d, 接收消息=%d, 发送消息=%d}",
                activeConnections, totalConnections, receivedMessages, sentMessages
            );
        }
    }
}