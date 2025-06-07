package com.knight.server.frame.thread;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能线程池管理器
 * 基于Java 17 ForkJoinPool实现高性能并发处理
 * 
 * 技术选型：ForkJoinPool + 自定义线程池管理 + 监控统计
 * 
 * @author lx
 */
public class VirtualThreadPoolManager {
    
    private static final Logger logger = LogManager.getLogger(VirtualThreadPoolManager.class);
    
    // 高性能线程执行器
    private static ExecutorService virtualThreadExecutor;
    
    // 传统线程池（用于CPU密集型任务）
    private static ThreadPoolExecutor cpuThreadPool;
    
    // 统计信息
    private static final AtomicLong virtualTaskCount = new AtomicLong(0);
    private static final AtomicLong cpuTaskCount = new AtomicLong(0);
    private static final AtomicLong completedTaskCount = new AtomicLong(0);
    
    static {
        initializeThreadPools();
    }
    
    /**
     * 初始化线程池
     */
    private static void initializeThreadPools() {
        // 创建虚拟线程执行器（用于I/O密集型任务）
        // Note: 在Java 17中使用ForkJoinPool代替虚拟线程
        virtualThreadExecutor = ForkJoinPool.commonPool();
        
        // 创建传统线程池（用于CPU密集型任务）
        int cpuCores = Runtime.getRuntime().availableProcessors();
        cpuThreadPool = new ThreadPoolExecutor(
            cpuCores,                           // 核心线程数
            cpuCores * 2,                       // 最大线程数
            60L, TimeUnit.SECONDS,              // 空闲线程存活时间
            new LinkedBlockingQueue<>(1000),    // 任务队列
            new CustomThreadFactory("CPU-Worker"), // 线程工厂
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
        
        logger.info("线程池管理器初始化完成，CPU核心数: {}", cpuCores);
    }
    
    /**
     * 提交高性能线程任务（适用于I/O密集型）
     * 
     * @param task 任务
     * @return Future对象
     */
    public static Future<?> submitVirtualTask(Runnable task) {
        virtualTaskCount.incrementAndGet();
        
        return virtualThreadExecutor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                task.run();
                long duration = System.currentTimeMillis() - startTime;
                
                completedTaskCount.incrementAndGet();
                
                if (duration > 1000) { // 记录耗时超过1秒的任务
                    logger.warn("高性能线程任务执行耗时: {}ms", duration);
                }
            } catch (Exception e) {
                logger.error("高性能线程任务执行异常", e);
            }
        });
    }
    
    /**
     * 提交高性能线程任务（带返回值）
     * 
     * @param task 任务
     * @param <T> 返回类型
     * @return Future对象
     */
    public static <T> Future<T> submitVirtualTask(Callable<T> task) {
        virtualTaskCount.incrementAndGet();
        
        return virtualThreadExecutor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                T result = task.call();
                long duration = System.currentTimeMillis() - startTime;
                
                completedTaskCount.incrementAndGet();
                
                if (duration > 1000) {
                    logger.warn("高性能线程任务执行耗时: {}ms", duration);
                }
                
                return result;
            } catch (Exception e) {
                logger.error("高性能线程任务执行异常", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 提交CPU密集型任务
     * 
     * @param task 任务
     * @return Future对象
     */
    public static Future<?> submitCpuTask(Runnable task) {
        cpuTaskCount.incrementAndGet();
        
        return cpuThreadPool.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                task.run();
                long duration = System.currentTimeMillis() - startTime;
                
                completedTaskCount.incrementAndGet();
                
                if (duration > 5000) { // CPU任务耗时阈值
                    logger.warn("CPU线程任务执行耗时: {}ms", duration);
                }
            } catch (Exception e) {
                logger.error("CPU线程任务执行异常", e);
            }
        });
    }
    
    /**
     * 提交CPU密集型任务（带返回值）
     * 
     * @param task 任务
     * @param <T> 返回类型
     * @return Future对象
     */
    public static <T> Future<T> submitCpuTask(Callable<T> task) {
        cpuTaskCount.incrementAndGet();
        
        return cpuThreadPool.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                T result = task.call();
                long duration = System.currentTimeMillis() - startTime;
                
                completedTaskCount.incrementAndGet();
                
                if (duration > 5000) {
                    logger.warn("CPU线程任务执行耗时: {}ms", duration);
                }
                
                return result;
            } catch (Exception e) {
                logger.error("CPU线程任务执行异常", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 获取线程池统计信息
     * 
     * @return 统计信息
     */
    public static ThreadPoolStats getStats() {
        return new ThreadPoolStats(
            virtualTaskCount.get(),
            cpuTaskCount.get(),
            completedTaskCount.get(),
            cpuThreadPool.getActiveCount(),
            cpuThreadPool.getPoolSize(),
            cpuThreadPool.getQueue().size()
        );
    }
    
    /**
     * 关闭线程池
     */
    public static void shutdown() {
        logger.info("开始关闭线程池...");
        
        virtualThreadExecutor.shutdown();
        cpuThreadPool.shutdown();
        
        try {
            if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
            if (!cpuThreadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                cpuThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            cpuThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("线程池已关闭");
    }
    
    /**
     * 自定义线程工厂
     */
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicLong threadNumber = new AtomicLong(1);
        private final String namePrefix;
        
        CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
    
    /**
     * 线程池统计信息
     */
    public static class ThreadPoolStats {
        private final long virtualTaskCount;
        private final long cpuTaskCount;
        private final long completedTaskCount;
        private final int activeCpuThreads;
        private final int totalCpuThreads;
        private final int queueSize;
        
        public ThreadPoolStats(long virtualTaskCount, long cpuTaskCount, 
                             long completedTaskCount, int activeCpuThreads, 
                             int totalCpuThreads, int queueSize) {
            this.virtualTaskCount = virtualTaskCount;
            this.cpuTaskCount = cpuTaskCount;
            this.completedTaskCount = completedTaskCount;
            this.activeCpuThreads = activeCpuThreads;
            this.totalCpuThreads = totalCpuThreads;
            this.queueSize = queueSize;
        }
        
        // Getters
        public long getVirtualTaskCount() { return virtualTaskCount; }
        public long getCpuTaskCount() { return cpuTaskCount; }
        public long getCompletedTaskCount() { return completedTaskCount; }
        public int getActiveCpuThreads() { return activeCpuThreads; }
        public int getTotalCpuThreads() { return totalCpuThreads; }
        public int getQueueSize() { return queueSize; }
        
        @Override
        public String toString() {
            return String.format(
                "ThreadPoolStats{高性能任务=%d, CPU任务=%d, 已完成=%d, 活跃CPU线程=%d, 总CPU线程=%d, 队列大小=%d}",
                virtualTaskCount, cpuTaskCount, completedTaskCount, 
                activeCpuThreads, totalCpuThreads, queueSize
            );
        }
    }
}