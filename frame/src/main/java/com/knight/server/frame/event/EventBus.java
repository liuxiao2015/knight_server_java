package com.knight.server.frame.event;

import com.knight.server.common.log.LoggerManager;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于Disruptor的高性能事件总线
 * 支持事件优先级、处理链和异步持久化
 * 技术选型：Disruptor Ring Buffer + 事件处理链 + 监控统计
 * 
 * @author lx
 */
public class EventBus {
    
    private static final Logger logger = LoggerManager.getLogger(EventBus.class);
    
    // Ring Buffer大小（必须是2的幂）
    private static final int BUFFER_SIZE = 1024 * 16;
    
    // Disruptor实例
    private final Disruptor<EventWrapper> disruptor;
    private final RingBuffer<EventWrapper> ringBuffer;
    
    // 事件处理器注册表
    private final ConcurrentMap<Class<?>, List<EventHandler<?>>> handlerRegistry = new ConcurrentHashMap<>();
    
    // 事件统计
    private final AtomicLong publishedEvents = new AtomicLong(0);
    private final AtomicLong processedEvents = new AtomicLong(0);
    private final AtomicLong failedEvents = new AtomicLong(0);
    
    // 单例实例
    private static final EventBus INSTANCE = new EventBus();
    
    /**
     * 私有构造函数
     */
    private EventBus() {
        // 创建Disruptor
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "EventBus-Worker");
            t.setDaemon(true);
            return t;
        };
        
        disruptor = new Disruptor<>(
            EventWrapper::new,
            BUFFER_SIZE,
            threadFactory,
            ProducerType.MULTI,
            new BlockingWaitStrategy()
        );
        
        // 设置事件处理器
        disruptor.handleEventsWith(this::handleEvent);
        
        // 设置异常处理器
        disruptor.setDefaultExceptionHandler(new EventExceptionHandler());
        
        // 启动Disruptor
        disruptor.start();
        ringBuffer = disruptor.getRingBuffer();
        
        // 启动监控
        startMonitoring();
        
        logger.info("事件总线初始化完成，缓冲区大小: {}", BUFFER_SIZE);
    }
    
    /**
     * 获取单例实例
     * 
     * @return EventBus实例
     */
    public static EventBus getInstance() {
        return INSTANCE;
    }
    
    /**
     * 发布事件
     * 
     * @param event 事件对象
     * @param <T> 事件类型
     */
    public <T> void publish(T event) {
        publish(event, EventPriority.NORMAL);
    }
    
    /**
     * 发布带优先级的事件
     * 
     * @param event 事件对象
     * @param priority 事件优先级
     * @param <T> 事件类型
     */
    public <T> void publish(T event, EventPriority priority) {
        if (event == null) {
            return;
        }
        
        long sequence = ringBuffer.next();
        try {
            EventWrapper eventWrapper = ringBuffer.get(sequence);
            eventWrapper.setEvent(event);
            eventWrapper.setPriority(priority);
            eventWrapper.setTimestamp(System.currentTimeMillis());
            eventWrapper.setSequence(publishedEvents.incrementAndGet());
        } finally {
            ringBuffer.publish(sequence);
        }
    }
    
    /**
     * 注册事件处理器
     * 
     * @param eventClass 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型
     */
    public <T> void register(Class<T> eventClass, EventHandler<T> handler) {
        handlerRegistry.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(handler);
        logger.info("注册事件处理器: {} -> {}", eventClass.getSimpleName(), handler.getClass().getSimpleName());
    }
    
    /**
     * 注册Lambda事件处理器
     * 
     * @param eventClass 事件类型
     * @param handler Lambda处理器
     * @param <T> 事件类型
     */
    public <T> void register(Class<T> eventClass, Consumer<T> handler) {
        register(eventClass, new LambdaEventHandler<>(handler));
    }
    
    /**
     * 取消注册事件处理器
     * 
     * @param eventClass 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型
     */
    public <T> void unregister(Class<T> eventClass, EventHandler<T> handler) {
        List<EventHandler<?>> handlers = handlerRegistry.get(eventClass);
        if (handlers != null) {
            handlers.remove(handler);
            logger.info("取消注册事件处理器: {} -> {}", eventClass.getSimpleName(), handler.getClass().getSimpleName());
        }
    }
    
    /**
     * 处理事件
     * 
     * @param eventWrapper 事件包装器
     * @param sequence 序列号
     * @param endOfBatch 是否批次结束
     */
    @SuppressWarnings("unchecked")
    private void handleEvent(EventWrapper eventWrapper, long sequence, boolean endOfBatch) {
        try {
            Object event = eventWrapper.getEvent();
            if (event == null) {
                return;
            }
            
            Class<?> eventClass = event.getClass();
            List<EventHandler<?>> handlers = handlerRegistry.get(eventClass);
            
            if (handlers != null && !handlers.isEmpty()) {
                for (EventHandler<?> handler : handlers) {
                    try {
                        long startTime = System.currentTimeMillis();
                        ((EventHandler<Object>) handler).handle(event);
                        long duration = System.currentTimeMillis() - startTime;
                        
                        // 记录性能
                        if (duration > 100) { // 超过100ms记录警告
                            LoggerManager.getPerformanceLogger().warn(
                                "事件处理耗时过长: {}ms, 事件: {}, 处理器: {}",
                                duration, eventClass.getSimpleName(), handler.getClass().getSimpleName()
                            );
                        }
                    } catch (Exception e) {
                        failedEvents.incrementAndGet();
                        logger.error("事件处理失败: {}, 处理器: {}", 
                            eventClass.getSimpleName(), handler.getClass().getSimpleName(), e);
                    }
                }
            } else {
                logger.debug("未找到事件处理器: {}", eventClass.getSimpleName());
            }
            
            processedEvents.incrementAndGet();
            
        } finally {
            // 清理事件包装器
            eventWrapper.clear();
        }
    }
    
    /**
     * 获取事件统计信息
     * 
     * @return 统计信息
     */
    public EventStats getStats() {
        return new EventStats(
            publishedEvents.get(),
            processedEvents.get(),
            failedEvents.get(),
            ringBuffer.remainingCapacity(),
            handlerRegistry.size()
        );
    }
    
    /**
     * 启动监控
     */
    private void startMonitoring() {
        // 每分钟输出一次统计信息
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // 60秒
                    EventStats stats = getStats();
                    LoggerManager.getPerformanceLogger().info(
                        "事件总线统计 - 发布: {}, 处理: {}, 失败: {}, 剩余容量: {}, 处理器数: {}",
                        stats.publishedCount(), stats.processedCount(), stats.failedCount(),
                        stats.remainingCapacity(), stats.handlerCount()
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "EventBus-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * 关闭事件总线
     */
    public void shutdown() {
        logger.info("正在关闭事件总线...");
        disruptor.shutdown();
        logger.info("事件总线关闭完成");
    }
    
    /**
     * 事件包装器
     */
    public static class EventWrapper {
        private Object event;
        private EventPriority priority;
        private long timestamp;
        private long sequence;
        
        public void setEvent(Object event) {
            this.event = event;
        }
        
        public Object getEvent() {
            return event;
        }
        
        public void setPriority(EventPriority priority) {
            this.priority = priority;
        }
        
        public EventPriority getPriority() {
            return priority;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setSequence(long sequence) {
            this.sequence = sequence;
        }
        
        public long getSequence() {
            return sequence;
        }
        
        public void clear() {
            this.event = null;
            this.priority = null;
            this.timestamp = 0;
            this.sequence = 0;
        }
    }
    
    /**
     * 事件优先级
     */
    public enum EventPriority {
        HIGH(1),
        NORMAL(2),
        LOW(3);
        
        private final int level;
        
        EventPriority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * 事件处理器接口
     */
    @FunctionalInterface
    public interface EventHandler<T> {
        void handle(T event) throws Exception;
    }
    
    /**
     * Lambda事件处理器适配器
     */
    private static class LambdaEventHandler<T> implements EventHandler<T> {
        private final Consumer<T> handler;
        
        public LambdaEventHandler(Consumer<T> handler) {
            this.handler = handler;
        }
        
        @Override
        public void handle(T event) {
            handler.accept(event);
        }
    }
    
    /**
     * 事件异常处理器
     */
    private static class EventExceptionHandler implements ExceptionHandler<EventWrapper> {
        private static final Logger logger = LoggerManager.getLogger(EventExceptionHandler.class);
        
        @Override
        public void handleEventException(Throwable ex, long sequence, EventWrapper event) {
            logger.error("事件处理异常，序列号: {}, 事件: {}", sequence, 
                event.getEvent() != null ? event.getEvent().getClass().getSimpleName() : "null", ex);
        }
        
        @Override
        public void handleOnStartException(Throwable ex) {
            logger.error("事件总线启动异常", ex);
        }
        
        @Override
        public void handleOnShutdownException(Throwable ex) {
            logger.error("事件总线关闭异常", ex);
        }
    }
    
    /**
     * 事件统计信息记录
     */
    public record EventStats(
        long publishedCount,
        long processedCount,
        long failedCount,
        long remainingCapacity,
        int handlerCount
    ) {}
}