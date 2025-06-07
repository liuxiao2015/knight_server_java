package com.knight.server.frame.network;

import com.knight.server.common.log.LoggerManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Netty的高性能网络服务器
 * 支持TCP长连接、心跳检测、消息压缩和流量控制
 * 技术选型：Netty 4.x NIO + 长连接管理 + 消息批量处理
 * 
 * @author lx
 */
public class NettyServer {
    
    private static final Logger logger = LoggerManager.getLogger(NettyServer.class);
    
    private final String host;
    private final int port;
    private final MessageHandler messageHandler;
    
    // Netty组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    // 连接管理
    private final ConnectionManager connectionManager;
    
    // 统计信息
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final AtomicLong messageReceived = new AtomicLong(0);
    private final AtomicLong messageSent = new AtomicLong(0);
    
    // 配置参数
    private int bossThreads = 1;
    private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;
    private int maxFrameLength = 1024 * 1024; // 1MB
    private int heartbeatInterval = 60; // 60秒
    
    /**
     * 构造函数
     * 
     * @param host 监听地址
     * @param port 监听端口
     * @param messageHandler 消息处理器
     */
    public NettyServer(String host, int port, MessageHandler messageHandler) {
        this.host = host;
        this.port = port;
        this.messageHandler = messageHandler;
        this.connectionManager = new ConnectionManager();
    }
    
    /**
     * 启动服务器
     * 
     * @throws Exception 启动异常
     */
    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(bossThreads, (ThreadFactory) r -> new Thread(r, "NettyServer-Boss"));
        workerGroup = new NioEventLoopGroup(workerThreads, (ThreadFactory) r -> new Thread(r, "NettyServer-Worker"));
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 心跳检测
                            pipeline.addLast(new IdleStateHandler(heartbeatInterval * 2, heartbeatInterval, 0, TimeUnit.SECONDS));
                            
                            // 帧解码器（防止粘包拆包）
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
                            pipeline.addLast(new LengthFieldPrepender(4));
                            
                            // 消息压缩（如果启用）
                            // pipeline.addLast(new SnappyFrameEncoder());
                            // pipeline.addLast(new SnappyFrameDecoder());
                            
                            // 业务处理器
                            pipeline.addLast(new ServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.SO_RCVBUF, 32 * 1024)
                    .childOption(ChannelOption.SO_SNDBUF, 32 * 1024);
            
            ChannelFuture future = bootstrap.bind(host, port).sync();
            serverChannel = future.channel();
            
            logger.info("Netty服务器启动成功: {}:{}, Boss线程: {}, Worker线程: {}", 
                host, port, bossThreads, workerThreads);
            
            // 启动统计线程
            startStatsThread();
            
        } catch (Exception e) {
            shutdown();
            throw e;
        }
    }
    
    /**
     * 关闭服务器
     */
    public void shutdown() {
        logger.info("正在关闭Netty服务器...");
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        connectionManager.closeAll();
        
        logger.info("Netty服务器关闭完成");
    }
    
    /**
     * 广播消息给所有连接
     * 
     * @param message 消息
     */
    public void broadcast(Object message) {
        connectionManager.broadcast(message);
    }
    
    /**
     * 发送消息给指定连接
     * 
     * @param connectionId 连接ID
     * @param message 消息
     * @return 是否成功
     */
    public boolean sendMessage(String connectionId, Object message) {
        return connectionManager.sendMessage(connectionId, message);
    }
    
    /**
     * 获取连接统计信息
     * 
     * @return 统计信息
     */
    public NetworkStats getStats() {
        return new NetworkStats(
            connectionCount.get(),
            messageReceived.get(),
            messageSent.get(),
            connectionManager.getActiveConnections()
        );
    }
    
    /**
     * 启动统计线程
     */
    private void startStatsThread() {
        Thread statsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // 每分钟输出一次
                    NetworkStats stats = getStats();
                    LoggerManager.getPerformanceLogger().info(
                        "网络统计 - 连接数: {}, 接收消息: {}, 发送消息: {}, 活跃连接: {}",
                        stats.connectionCount(), stats.messageReceived(), stats.messageSent(), stats.activeConnections()
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "NettyServer-Stats");
        statsThread.setDaemon(true);
        statsThread.start();
    }
    
    /**
     * 服务器处理器
     */
    private class ServerHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String connectionId = connectionManager.addConnection(ctx.channel());
            connectionCount.incrementAndGet();
            logger.debug("新连接建立: {}, 连接ID: {}", ctx.channel().remoteAddress(), connectionId);
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            connectionManager.removeConnection(ctx.channel());
            connectionCount.decrementAndGet();
            logger.debug("连接断开: {}", ctx.channel().remoteAddress());
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            messageReceived.incrementAndGet();
            
            try {
                if (messageHandler != null) {
                    String connectionId = connectionManager.getConnectionId(ctx.channel());
                    messageHandler.handleMessage(connectionId, msg);
                }
            } catch (Exception e) {
                logger.error("处理消息异常: {}", ctx.channel().remoteAddress(), e);
            } finally {
                // 释放消息资源
                if (msg instanceof io.netty.util.ReferenceCounted) {
                    ((io.netty.util.ReferenceCounted) msg).release();
                }
            }
        }
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
                io.netty.handler.timeout.IdleStateEvent event = (io.netty.handler.timeout.IdleStateEvent) evt;
                if (event.state() == io.netty.handler.timeout.IdleState.READER_IDLE) {
                    logger.debug("连接心跳超时，关闭连接: {}", ctx.channel().remoteAddress());
                    ctx.close();
                }
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("连接异常: {}", ctx.channel().remoteAddress(), cause);
            ctx.close();
        }
    }
    
    /**
     * 消息处理器接口
     */
    @FunctionalInterface
    public interface MessageHandler {
        void handleMessage(String connectionId, Object message) throws Exception;
    }
    
    /**
     * 网络统计信息记录
     */
    public record NetworkStats(
        int connectionCount,
        long messageReceived,
        long messageSent,
        int activeConnections
    ) {}
    
    // Setter方法用于配置
    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }
    
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }
    
    public void setMaxFrameLength(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }
    
    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }
}