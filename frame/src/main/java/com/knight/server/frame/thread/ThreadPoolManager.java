package com.knight.server.frame.thread;

import com.knight.server.common.log.LoggerManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能线程池管理器
 * 支持任务优先级调度和线程监控统计
 * 技术选型：Java 17 CompletableFuture + 优先级队列 + 监控统计
 * 
 * @author lx
 */
public class ThreadPoolManager {
    
    private static final Logger logger = LoggerManager.getLogger(ThreadPoolManager.class);
    
    // I/O密集型线程池（用于I/O密集型任务）
    private static final ExecutorService IO_EXECUTOR;
    
    // 传统线程池（用于CPU密集型任务）
    private static final ThreadPoolExecutor CPU_EXECUTOR;
    
    // 定时任务执行器
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR;
    
    // 任务统计
    private static final AtomicLong IO_TASK_COUNT = new AtomicLong(0);
    private static final AtomicLong CPU_TASK_COUNT = new AtomicLong(0);
    private static final AtomicLong SCHEDULED_TASK_COUNT = new AtomicLong(0);
    
    static {
        // 初始化I/O密集型线程池
        int cpuCores = Runtime.getRuntime().availableProcessors();
        IO_EXECUTOR = new ThreadPoolExecutor(
            cpuCores * 2, cpuCores * 4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new NamedThreadFactory("IO-Pool"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 初始化CPU密集型线程池
        CPU_EXECUTOR = new ThreadPoolExecutor(
            cpuCores, cpuCores * 2,
            60L, TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(),
            new NamedThreadFactory("CPU-Pool"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 初始化定时任务执行器
        SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(
            Math.max(4, cpuCores / 2),
            new NamedThreadFactory("Scheduled-Pool")
        );
        
        // 启动监控
        startMonitoring();
        
        logger.info("线程池管理器初始化完成 - CPU核心数: {}", cpuCores);
    }
    
    /**
     * 提交I/O密集型任务
     * 
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> submitIOTask(Runnable task) {
        IO_TASK_COUNT.incrementAndGet();
        return CompletableFuture.runAsync(task, IO_EXECUTOR);
    }
    
    /**
     * 提交I/O密集型任务（有返回值）
     * 
     * @param task 任务
     * @param <T> 返回类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> submitIOTask(Callable<T> task) {
        IO_TASK_COUNT.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, IO_EXECUTOR);
    }
    
    /**
     * 提交CPU密集型任务
     * 
     * @param task 任务
     * @return Future
     */
    public static Future<?> submitCpuTask(Runnable task) {
        CPU_TASK_COUNT.incrementAndGet();
        return CPU_EXECUTOR.submit(task);
    }
    
    /**
     * 提交优先级任务
     * 
     * @param task 任务
     * @param priority 优先级（数值越小优先级越高）
     * @return Future
     */
    public static Future<?> submitPriorityTask(Runnable task, int priority) {
        CPU_TASK_COUNT.incrementAndGet();
        return CPU_EXECUTOR.submit(new PriorityTask(task, priority));
    }
    
    /**
     * 提交CPU密集型任务（有返回值）
     * 
     * @param task 任务
     * @param <T> 返回类型
     * @return Future
     */
    public static <T> Future<T> submitCpuTask(Callable<T> task) {
        CPU_TASK_COUNT.incrementAndGet();
        return CPU_EXECUTOR.submit(task);
    }
    
    /**
     * 定时执行任务
     * 
     * @param task 任务
     * @param delay 延迟时间
     * @param unit 时间单位
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        SCHEDULED_TASK_COUNT.incrementAndGet();
        return SCHEDULED_EXECUTOR.schedule(task, delay, unit);
    }
    
    /**
     * 定时执行任务（有返回值）
     * 
     * @param task 任务
     * @param delay 延迟时间
     * @param unit 时间单位
     * @param <T> 返回类型
     * @return ScheduledFuture
     */
    public static <T> ScheduledFuture<T> schedule(Callable<T> task, long delay, TimeUnit unit) {
        SCHEDULED_TASK_COUNT.incrementAndGet();
        return SCHEDULED_EXECUTOR.schedule(task, delay, unit);
    }
    
    /**
     * 周期性执行任务
     * 
     * @param task 任务
     * @param initialDelay 初始延迟
     * @param period 执行周期
     * @param unit 时间单位
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        SCHEDULED_TASK_COUNT.incrementAndGet();
        return SCHEDULED_EXECUTOR.scheduleAtFixedRate(task, initialDelay, period, unit);
    }
    
    /**
     * 获取线程池统计信息
     * 
     * @return 统计信息
     */
    public static ThreadPoolStats getStats() {
        return new ThreadPoolStats(
            IO_TASK_COUNT.get(),
            CPU_TASK_COUNT.get(),
            SCHEDULED_TASK_COUNT.get(),
            CPU_EXECUTOR.getActiveCount(),
            CPU_EXECUTOR.getPoolSize(),
            CPU_EXECUTOR.getQueue().size()
        );
    }
    
    /**
     * 启动监控
     */
    private static void startMonitoring() {
        SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            ThreadPoolStats stats = getStats();
            LoggerManager.getPerformanceLogger().info(
                "线程池统计 - I/O线程任务: {}, CPU任务: {}, 定时任务: {}, CPU活跃线程: {}, CPU池大小: {}, CPU队列长度: {}",
                stats.ioTaskCount(), stats.cpuTaskCount(), stats.scheduledTaskCount(),
                stats.cpuActiveThreads(), stats.cpuPoolSize(), stats.cpuQueueSize()
            );
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * 关闭线程池
     */
    public static void shutdown() {
        logger.info("正在关闭线程池...");
        
        IO_EXECUTOR.shutdown();
        CPU_EXECUTOR.shutdown();
        SCHEDULED_EXECUTOR.shutdown();
        
        try {
            if (!IO_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                IO_EXECUTOR.shutdownNow();
            }
            if (!CPU_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                CPU_EXECUTOR.shutdownNow();
            }
            if (!SCHEDULED_EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                SCHEDULED_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IO_EXECUTOR.shutdownNow();
            CPU_EXECUTOR.shutdownNow();
            SCHEDULED_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("线程池关闭完成");
    }
    
    /**
     * 命名线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        public NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
    
    /**
     * 优先级任务包装器
     */
    private static class PriorityTask implements Runnable, Comparable<PriorityTask> {
        private final Runnable task;
        private final int priority;
        private final long sequence;
        private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);
        
        public PriorityTask(Runnable task, int priority) {
            this.task = task;
            this.priority = priority;
            this.sequence = SEQUENCE_GENERATOR.incrementAndGet();
        }
        
        @Override
        public void run() {
            task.run();
        }
        
        @Override
        public int compareTo(PriorityTask other) {
            int result = Integer.compare(this.priority, other.priority);
            if (result == 0) {
                result = Long.compare(this.sequence, other.sequence);
            }
            return result;
        }
    }
    
    /**
     * 线程池统计信息记录
     */
    public record ThreadPoolStats(
        long ioTaskCount,
        long cpuTaskCount, 
        long scheduledTaskCount,
        int cpuActiveThreads,
        int cpuPoolSize,
        int cpuQueueSize
    ) {}
}