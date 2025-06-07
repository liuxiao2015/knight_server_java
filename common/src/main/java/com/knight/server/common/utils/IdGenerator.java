package com.knight.server.common.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法ID生成器
 * 基于Twitter Snowflake算法实现分布式唯一ID生成
 * 64位ID结构：1位符号位 + 41位时间戳 + 10位机器ID + 12位序列号
 * 技术选型：Snowflake算法 + 原子操作保证线程安全
 * 
 * @author lx
 */
public class IdGenerator {
    
    // 起始时间戳（2024-01-01 00:00:00）
    private static final long EPOCH = 1704067200000L;
    
    // 机器ID位数
    private static final long MACHINE_ID_BITS = 10L;
    // 序列号位数
    private static final long SEQUENCE_BITS = 12L;
    
    // 机器ID最大值
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    // 序列号最大值
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    
    // 机器ID左移位数
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    // 时间戳左移位数
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    
    private final long machineId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private volatile long lastTimestamp = -1L;
    
    private static final IdGenerator INSTANCE = new IdGenerator();
    
    /**
     * 私有构造函数，使用默认机器ID
     */
    private IdGenerator() {
        this(getDefaultMachineId());
    }
    
    /**
     * 构造函数
     * 
     * @param machineId 机器ID（0-1023）
     */
    public IdGenerator(long machineId) {
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException(
                String.format("机器ID必须在0到%d之间", MAX_MACHINE_ID));
        }
        this.machineId = machineId;
    }
    
    /**
     * 获取单例实例
     * 
     * @return IdGenerator实例
     */
    public static IdGenerator getInstance() {
        return INSTANCE;
    }
    
    /**
     * 生成下一个ID
     * 
     * @return 唯一ID
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();
        
        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                String.format("时钟回拨异常，拒绝生成ID。当前时间：%d，上次时间：%d", 
                timestamp, lastTimestamp));
        }
        
        // 同一毫秒内序列号递增
        if (lastTimestamp == timestamp) {
            long currentSequence = sequence.incrementAndGet() & MAX_SEQUENCE;
            if (currentSequence == 0) {
                // 序列号溢出，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
                sequence.set(0L);
            }
        } else {
            // 新的毫秒，序列号重置
            sequence.set(0L);
        }
        
        lastTimestamp = timestamp;
        
        // 组装ID
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence.get();
    }
    
    /**
     * 生成字符串格式的ID
     * 
     * @return 字符串ID
     */
    public String nextStringId() {
        return String.valueOf(nextId());
    }
    
    /**
     * 解析ID获取时间戳
     * 
     * @param id 雪花ID
     * @return 时间戳
     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }
    
    /**
     * 解析ID获取机器ID
     * 
     * @param id 雪花ID
     * @return 机器ID
     */
    public static long parseMachineId(long id) {
        return (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }
    
    /**
     * 解析ID获取序列号
     * 
     * @param id 雪花ID
     * @return 序列号
     */
    public static long parseSequence(long id) {
        return id & MAX_SEQUENCE;
    }
    
    /**
     * 等待下一毫秒
     * 
     * @param lastTimestamp 上次时间戳
     * @return 新的时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }
    
    /**
     * 获取当前时间戳
     * 
     * @return 当前时间戳
     */
    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
    
    /**
     * 获取默认机器ID
     * 基于MAC地址和进程ID生成
     * 
     * @return 机器ID
     */
    private static long getDefaultMachineId() {
        try {
            // 获取MAC地址
            java.net.NetworkInterface network = java.net.NetworkInterface.getNetworkInterfaces()
                    .nextElement();
            byte[] mac = network.getHardwareAddress();
            
            if (mac != null && mac.length >= 6) {
                long machineId = ((0x000000FF & (long) mac[mac.length - 1]) 
                                | (0x0000FF00 & (((long) mac[mac.length - 2]) << 8))) >> 6;
                return machineId & MAX_MACHINE_ID;
            }
        } catch (Exception e) {
            // 忽略异常，使用默认值
        }
        
        // 如果无法获取MAC地址，使用进程ID
        return (java.lang.management.ManagementFactory.getRuntimeMXBean().getName().hashCode() 
                & 0xFFFF) % (MAX_MACHINE_ID + 1);
    }
    
    /**
     * 获取机器ID
     * 
     * @return 当前机器ID
     */
    public long getMachineId() {
        return machineId;
    }
    
    /**
     * 获取当前序列号
     * 
     * @return 当前序列号
     */
    public long getCurrentSequence() {
        return sequence.get();
    }
}