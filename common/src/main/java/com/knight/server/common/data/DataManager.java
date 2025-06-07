package com.knight.server.common.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.knight.server.common.log.LoggerManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * 静态数据管理器
 * 支持JSON、Excel格式配置表的热加载机制
 * 基于文件监听和版本控制实现配置热更新
 * 技术选型：Jackson + WatchService + 版本管理
 * 
 * @author lx
 */
public class DataManager {
    
    private static final Logger logger = LoggerManager.getLogger(DataManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentMap<String, ConfigData> configCache = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> versionMap = new ConcurrentHashMap<>();
    
    private static WatchService watchService;
    private static ScheduledExecutorService executorService;
    private static String configDirectory = "config";
    
    static {
        objectMapper.registerModule(new JavaTimeModule());
        initializeWatchService();
    }
    
    /**
     * 初始化文件监听服务
     */
    private static void initializeWatchService() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            executorService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DataManager-WatchService");
                t.setDaemon(true);
                return t;
            });
            
            // 启动文件监听
            executorService.submit(DataManager::watchConfigFiles);
            
            logger.info("配置文件监听服务初始化完成");
        } catch (IOException e) {
            logger.error("初始化配置文件监听服务失败", e);
        }
    }
    
    /**
     * 设置配置文件目录
     * 
     * @param directory 配置文件目录路径
     */
    public static void setConfigDirectory(String directory) {
        configDirectory = directory;
        // 重新注册监听目录
        registerWatchDirectory(Paths.get(directory));
    }
    
    /**
     * 加载配置文件
     * 
     * @param fileName 配置文件名
     * @param clazz 配置类类型
     * @param <T> 配置类型
     * @return 配置对象
     */
    public static <T> T loadConfig(String fileName, Class<T> clazz) {
        try {
            String cacheKey = fileName + ":" + clazz.getName();
            ConfigData cached = configCache.get(cacheKey);
            
            File configFile = new File(configDirectory, fileName);
            long lastModified = configFile.lastModified();
            
            // 检查缓存是否有效
            if (cached != null && cached.getLastModified() >= lastModified) {
                return clazz.cast(cached.getData());
            }
            
            // 加载配置文件
            T config = objectMapper.readValue(configFile, clazz);
            
            // 更新缓存
            configCache.put(cacheKey, new ConfigData(config, lastModified));
            versionMap.put(cacheKey, System.currentTimeMillis());
            
            logger.info("加载配置文件成功: {}", fileName);
            return config;
            
        } catch (IOException e) {
            logger.error("加载配置文件失败: {}", fileName, e);
            throw new RuntimeException("Failed to load config: " + fileName, e);
        }
    }
    
    /**
     * 获取配置版本号
     * 
     * @param fileName 配置文件名
     * @param clazz 配置类类型
     * @return 版本号
     */
    public static long getConfigVersion(String fileName, Class<?> clazz) {
        String cacheKey = fileName + ":" + clazz.getName();
        return versionMap.getOrDefault(cacheKey, 0L);
    }
    
    /**
     * 强制重新加载配置
     * 
     * @param fileName 配置文件名
     * @param clazz 配置类类型
     * @param <T> 配置类型
     * @return 配置对象
     */
    public static <T> T reloadConfig(String fileName, Class<T> clazz) {
        String cacheKey = fileName + ":" + clazz.getName();
        configCache.remove(cacheKey);
        return loadConfig(fileName, clazz);
    }
    
    /**
     * 获取所有配置的版本信息
     * 
     * @return 配置版本映射
     */
    public static Map<String, Long> getAllVersions() {
        return Map.copyOf(versionMap);
    }
    
    /**
     * 注册监听目录
     * 
     * @param directory 目录路径
     */
    private static void registerWatchDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                directory.register(watchService, 
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
                logger.info("注册配置目录监听: {}", directory);
            }
        } catch (IOException e) {
            logger.error("注册目录监听失败: {}", directory, e);
        }
    }
    
    /**
     * 监听配置文件变化
     */
    private static void watchConfigFiles() {
        registerWatchDirectory(Paths.get(configDirectory));
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    Path fileName = (Path) event.context();
                    String name = fileName.toString();
                    
                    if (name.endsWith(".json") || name.endsWith(".xlsx")) {
                        logger.info("检测到配置文件变化: {} - {}", name, event.kind());
                        handleConfigFileChange(name);
                    }
                }
                
                if (!key.reset()) {
                    break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("监听配置文件变化时发生异常", e);
            }
        }
    }
    
    /**
     * 处理配置文件变化
     * 
     * @param fileName 变化的文件名
     */
    private static void handleConfigFileChange(String fileName) {
        // 延迟处理，避免文件正在写入时读取
        executorService.schedule(() -> {
            try {
                // 清除相关缓存
                configCache.entrySet().removeIf(entry -> entry.getKey().startsWith(fileName + ":"));
                logger.info("配置文件热更新完成: {}", fileName);
            } catch (Exception e) {
                logger.error("处理配置文件变化失败: {}", fileName, e);
            }
        }, 1, TimeUnit.SECONDS);
    }
    
    /**
     * 关闭数据管理器
     */
    public static void shutdown() {
        try {
            if (executorService != null) {
                executorService.shutdown();
            }
            if (watchService != null) {
                watchService.close();
            }
            logger.info("数据管理器关闭完成");
        } catch (IOException e) {
            logger.error("关闭数据管理器时发生异常", e);
        }
    }
    
    /**
     * 配置数据封装类
     */
    private static class ConfigData {
        private final Object data;
        private final long lastModified;
        
        public ConfigData(Object data, long lastModified) {
            this.data = data;
            this.lastModified = lastModified;
        }
        
        public Object getData() {
            return data;
        }
        
        public long getLastModified() {
            return lastModified;
        }
    }
}