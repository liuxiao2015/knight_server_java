package com.knight.server.frame.event;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能事件总线
 * 基于Disruptor实现超高性能事件处理
 * 
 * 技术选型：Disruptor环形缓冲区 + 多生产者多消费者模式
 * 
 * @author lx
 */
public class HighPerformanceEventBus {
    
    private static final Logger logger = LogManager.getLogger(HighPerformanceEventBus.class);
    
    // 环形缓冲区大小 (必须是2的幂)
    private static final int BUFFER_SIZE = 1024 * 16;
    
    // Disruptor实例
    private final Disruptor<GameEvent> disruptor;
    private final RingBuffer<GameEvent> ringBuffer;
    
    // 统计信息
    private final AtomicLong eventCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    
    public HighPerformanceEventBus() {
        // 创建事件工厂
        EventFactory<GameEvent> eventFactory = GameEvent::new;
        
        // 创建线程工厂
        ThreadFactory threadFactory = new EventThreadFactory();
        
        // 创建Disruptor
        disruptor = new Disruptor<>(
            eventFactory,
            BUFFER_SIZE,
            threadFactory,
            ProducerType.MULTI,    // 多生产者模式
            new YieldingWaitStrategy()  // 等待策略
        );
        
        // 设置事件处理器
        disruptor.handleEventsWith(new GameEventHandler());
        
        // 设置异常处理器
        disruptor.setDefaultExceptionHandler(new EventExceptionHandler());
        
        // 启动Disruptor
        disruptor.start();
        ringBuffer = disruptor.getRingBuffer();
        
        logger.info("高性能事件总线已启动，缓冲区大小: {}", BUFFER_SIZE);
    }
    
    /**
     * 发布事件
     * 
     * @param eventType 事件类型
     * @param data 事件数据
     * @param priority 优先级
     */
    public void publishEvent(String eventType, Object data, int priority) {
        long sequence = ringBuffer.next();
        try {
            GameEvent event = ringBuffer.get(sequence);
            event.setEventType(eventType);
            event.setData(data);
            event.setPriority(priority);
            event.setTimestamp(System.currentTimeMillis());
            event.setSequence(sequence);
            
            eventCount.incrementAndGet();
        } finally {
            ringBuffer.publish(sequence);
        }
    }
    
    /**
     * 发布高优先级事件
     * 
     * @param eventType 事件类型
     * @param data 事件数据
     */
    public void publishHighPriorityEvent(String eventType, Object data) {
        publishEvent(eventType, data, EventPriority.HIGH.getValue());
    }
    
    /**
     * 发布普通事件
     * 
     * @param eventType 事件类型
     * @param data 事件数据
     */
    public void publishEvent(String eventType, Object data) {
        publishEvent(eventType, data, EventPriority.NORMAL.getValue());
    }
    
    /**
     * 获取统计信息
     * 
     * @return 事件统计
     */
    public EventStats getStats() {
        return new EventStats(
            eventCount.get(),
            processedCount.get(),
            ringBuffer.remainingCapacity()
        );
    }
    
    /**
     * 关闭事件总线
     */
    public void shutdown() {
        logger.info("开始关闭事件总线...");
        disruptor.shutdown();
        logger.info("事件总线已关闭");
    }
    
    /**
     * 游戏事件类
     */
    public static class GameEvent {
        private String eventType;
        private Object data;
        private int priority;
        private long timestamp;
        private long sequence;
        
        public GameEvent() {}
        
        // Getters and Setters
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public long getSequence() { return sequence; }
        public void setSequence(long sequence) { this.sequence = sequence; }
        
        @Override
        public String toString() {
            return String.format("GameEvent{type='%s', priority=%d, timestamp=%d, sequence=%d}",
                               eventType, priority, timestamp, sequence);
        }
    }
    
    /**
     * 事件处理器
     */
    private class GameEventHandler implements EventHandler<GameEvent> {
        
        @Override
        public void onEvent(GameEvent event, long sequence, boolean endOfBatch) {
            try {
                // 处理事件
                processEvent(event);
                processedCount.incrementAndGet();
                
                // 记录处理耗时
                long processingTime = System.currentTimeMillis() - event.getTimestamp();
                if (processingTime > 100) { // 超过100ms记录警告
                    logger.warn("事件处理耗时: {}ms, 事件: {}", processingTime, event);
                }
                
            } catch (Exception e) {
                logger.error("事件处理异常: {}", event, e);
            }
        }
        
        private void processEvent(GameEvent event) {
            // 根据事件类型进行分发处理
            switch (event.getEventType()) {
                case "player.login":
                    handlePlayerLogin(event);
                    break;
                case "player.logout":
                    handlePlayerLogout(event);
                    break;
                case "chat.message":
                    handleChatMessage(event);
                    break;
                case "battle.result":
                    handleBattleResult(event);
                    break;
                default:
                    logger.debug("未处理的事件类型: {}", event.getEventType());
                    break;
            }
        }
        
        private void handlePlayerLogin(GameEvent event) {
            logger.debug("处理玩家登录事件: {}", event.getData());
            // 具体的登录处理逻辑
        }
        
        private void handlePlayerLogout(GameEvent event) {
            logger.debug("处理玩家登出事件: {}", event.getData());
            // 具体的登出处理逻辑
        }
        
        private void handleChatMessage(GameEvent event) {
            logger.debug("处理聊天消息事件: {}", event.getData());
            // 具体的聊天处理逻辑
        }
        
        private void handleBattleResult(GameEvent event) {
            logger.debug("处理战斗结果事件: {}", event.getData());
            // 具体的战斗结果处理逻辑
        }
    }
    
    /**
     * 异常处理器
     */
    private static class EventExceptionHandler implements ExceptionHandler<GameEvent> {
        
        @Override
        public void handleEventException(Throwable ex, long sequence, GameEvent event) {
            logger.error("事件处理异常 - 序号: {}, 事件: {}", sequence, event, ex);
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
     * 事件线程工厂
     */
    private static class EventThreadFactory implements ThreadFactory {
        private final AtomicLong threadNumber = new AtomicLong(1);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "EventProcessor-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.MAX_PRIORITY); // 事件处理使用最高优先级
            return t;
        }
    }
    
    /**
     * 事件优先级枚举
     */
    public enum EventPriority {
        LOW(1),
        NORMAL(5),
        HIGH(10),
        CRITICAL(20);
        
        private final int value;
        
        EventPriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 事件统计信息
     */
    public static class EventStats {
        private final long totalEvents;
        private final long processedEvents;
        private final long remainingCapacity;
        
        public EventStats(long totalEvents, long processedEvents, long remainingCapacity) {
            this.totalEvents = totalEvents;
            this.processedEvents = processedEvents;
            this.remainingCapacity = remainingCapacity;
        }
        
        public long getTotalEvents() { return totalEvents; }
        public long getProcessedEvents() { return processedEvents; }
        public long getRemainingCapacity() { return remainingCapacity; }
        public long getPendingEvents() { return totalEvents - processedEvents; }
        
        @Override
        public String toString() {
            return String.format(
                "EventStats{总事件=%d, 已处理=%d, 等待处理=%d, 剩余容量=%d}",
                totalEvents, processedEvents, getPendingEvents(), remainingCapacity
            );
        }
    }
}