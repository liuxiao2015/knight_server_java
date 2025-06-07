package com.knight.server.common.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法ID生成器
 * 分布式系统唯一ID生成，支持高并发
 * 
 * 技术选型：雪花算法(64位) = 1位符号位 + 41位时间戳 + 10位机器ID + 12位序列号
 * 
 * @author lx
 */
public class SnowflakeIdGenerator {
    
    // 开始时间戳 (2024-01-01 00:00:00)
    private static final long START_TIMESTAMP = 1704067200000L;
    
    // 各部分位数
    private static final long SEQUENCE_BIT = 12; // 序列号占用位数
    private static final long MACHINE_BIT = 5;   // 机器标识占用位数
    private static final long DATACENTER_BIT = 5; // 数据中心占用位数
    
    // 各部分最大值
    private static final long MAX_DATACENTER_NUM = -1L ^ (-1L << DATACENTER_BIT);
    private static final long MAX_MACHINE_NUM = -1L ^ (-1L << MACHINE_BIT);
    private static final long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BIT);
    
    // 各部分向左的位移
    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;
    
    private final long datacenterId;  // 数据中心ID
    private final long machineId;     // 机器ID
    private AtomicLong sequence = new AtomicLong(0L); // 序列号
    private long lastTimestamp = -1L; // 上一次时间戳
    
    /**
     * 构造函数
     * 
     * @param datacenterId 数据中心ID (0-31)
     * @param machineId 机器ID (0-31)
     */
    public SnowflakeIdGenerator(long datacenterId, long machineId) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("数据中心ID不能大于" + MAX_DATACENTER_NUM + "或小于0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("机器ID不能大于" + MAX_MACHINE_NUM + "或小于0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }
    
    /**
     * 产生下一个ID
     * 
     * @return 唯一ID
     */
    public synchronized long nextId() {
        long timestamp = getCurrentTimestamp();
        
        // 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回退，拒绝生成ID");
        }
        
        if (lastTimestamp == timestamp) {
            // 相同毫秒内，序列号自增
            long currentSequence = sequence.incrementAndGet();
            if (currentSequence > MAX_SEQUENCE) {
                // 同一毫秒的序列数已经达到最大，等待下一毫秒
                timestamp = getNextMill();
                sequence.set(0L);
            }
        } else {
            // 不同毫秒内，序列号置为0
            sequence.set(0L);
        }
        
        lastTimestamp = timestamp;
        
        // 移位并通过或运算拼到一起组成64位的ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_LEFT)
                | (datacenterId << DATACENTER_LEFT)
                | (machineId << MACHINE_LEFT)
                | sequence.get();
    }
    
    /**
     * 获取下一毫秒
     */
    private long getNextMill() {
        long mill = getCurrentTimestamp();
        while (mill <= lastTimestamp) {
            mill = getCurrentTimestamp();
        }
        return mill;
    }
    
    /**
     * 获取当前时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * 从ID中解析时间戳
     * 
     * @param id 雪花ID
     * @return 时间戳
     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_LEFT) + START_TIMESTAMP;
    }
    
    /**
     * 从ID中解析数据中心ID
     * 
     * @param id 雪花ID
     * @return 数据中心ID
     */
    public static long parseDatacenterId(long id) {
        return (id << (64 - TIMESTAMP_LEFT)) >> (64 - DATACENTER_BIT);
    }
    
    /**
     * 从ID中解析机器ID
     * 
     * @param id 雪花ID
     * @return 机器ID
     */
    public static long parseMachineId(long id) {
        return (id << (64 - DATACENTER_LEFT)) >> (64 - MACHINE_BIT);
    }
    
    /**
     * 从ID中解析序列号
     * 
     * @param id 雪花ID
     * @return 序列号
     */
    public static long parseSequence(long id) {
        return id & MAX_SEQUENCE;
    }
}