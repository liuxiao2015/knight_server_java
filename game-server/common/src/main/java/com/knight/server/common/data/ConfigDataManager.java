package com.knight.server.common.data;

import com.knight.server.common.utils.JsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 配置数据管理器
 * 支持JSON、Excel格式配置表，具备热加载和版本管理功能
 * 
 * 技术选型：文件监听 + 内存缓存 + 版本控制
 * 
 * @author lx
 */
public class ConfigDataManager {
    
    private static final Logger logger = LogManager.getLogger(ConfigDataManager.class);
    
    // 配置缓存 - 文件路径 -> 配置内容
    private static final Map<String, Object> configCache = new ConcurrentHashMap<>();
    
    // 版本管理 - 文件路径 -> 版本号
    private static final Map<String, AtomicLong> versionMap = new ConcurrentHashMap<>();
    
    // 文件监听服务
    private static WatchService watchService;
    private static volatile boolean watchEnabled = false;
    
    // 配置文件根目录
    private static String configRoot = "config/";
    
    static {
        try {
            // 初始化文件监听服务
            watchService = FileSystems.getDefault().newWatchService();
            startWatchService();
        } catch (IOException e) {
            logger.error("初始化文件监听服务失败", e);
        }
    }
    
    /**
     * 设置配置文件根目录
     * 
     * @param root 根目录路径
     */
    public static void setConfigRoot(String root) {
        configRoot = root;
        logger.info("配置文件根目录设置为: {}", configRoot);
    }
    
    /**
     * 加载配置文件
     * 
     * @param configPath 配置文件路径（相对于根目录）
     * @param clazz 配置类型
     * @param <T> 泛型类型
     * @return 配置对象
     */
    public static <T> T loadConfig(String configPath, Class<T> clazz) {
        String fullPath = configRoot + configPath;
        File configFile = new File(fullPath);
        
        if (!configFile.exists()) {
            logger.warn("配置文件不存在: {}", fullPath);
            return null;
        }
        
        try {
            // 检查缓存
            Object cached = configCache.get(fullPath);
            if (cached != null && clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
            
            // 读取配置文件
            String content = Files.readString(configFile.toPath());
            T config = null;
            
            if (configPath.endsWith(".json")) {
                config = JsonUtils.fromJsonString(content, clazz);
            } else if (configPath.endsWith(".xml")) {
                // 这里可以扩展XML解析
                logger.warn("暂不支持XML格式: {}", configPath);
            } else {
                logger.warn("不支持的配置格式: {}", configPath);
                return null;
            }
            
            if (config != null) {
                // 更新缓存和版本
                configCache.put(fullPath, config);
                versionMap.computeIfAbsent(fullPath, k -> new AtomicLong(0)).incrementAndGet();
                
                // 添加文件监听
                registerWatchPath(configFile.getParentFile().toPath());
                
                logger.info("配置文件加载成功: {} (版本: {})", 
                           fullPath, versionMap.get(fullPath).get());
            }
            
            return config;
            
        } catch (IOException e) {
            logger.error("读取配置文件失败: {}", fullPath, e);
            return null;
        }
    }
    
    /**
     * 重载指定配置文件
     * 
     * @param configPath 配置文件路径
     * @param clazz 配置类型
     * @param <T> 泛型类型
     * @return 重载后的配置对象
     */
    public static <T> T reloadConfig(String configPath, Class<T> clazz) {
        String fullPath = configRoot + configPath;
        
        // 清除缓存
        configCache.remove(fullPath);
        
        logger.info("重载配置文件: {}", fullPath);
        return loadConfig(configPath, clazz);
    }
    
    /**
     * 获取配置版本号
     * 
     * @param configPath 配置文件路径
     * @return 版本号
     */
    public static long getConfigVersion(String configPath) {
        String fullPath = configRoot + configPath;
        AtomicLong version = versionMap.get(fullPath);
        return version != null ? version.get() : 0L;
    }
    
    /**
     * 清除所有配置缓存
     */
    public static void clearCache() {
        configCache.clear();
        logger.info("已清除所有配置缓存");
    }
    
    /**
     * 获取缓存状态信息
     * 
     * @return 缓存信息
     */
    public static Map<String, Object> getCacheInfo() {
        Map<String, Object> info = new ConcurrentHashMap<>();
        info.put("cacheSize", configCache.size());
        info.put("versions", versionMap);
        return info;
    }
    
    /**
     * 启动文件监听服务
     */
    private static void startWatchService() {
        watchEnabled = true;
        Thread watchThread = new Thread(() -> {
            while (watchEnabled) {
                try {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        
                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path filePath = (Path) event.context();
                            handleFileChange(filePath);
                        }
                    }
                    key.reset();
                } catch (InterruptedException e) {
                    logger.info("文件监听服务被中断");
                    break;
                } catch (Exception e) {
                    logger.error("文件监听异常", e);
                }
            }
        });
        
        watchThread.setDaemon(true);
        watchThread.setName("ConfigFileWatcher");
        watchThread.start();
        
        logger.info("文件监听服务已启动");
    }
    
    /**
     * 注册监听路径
     * 
     * @param path 监听路径
     */
    private static void registerWatchPath(Path path) {
        try {
            path.register(watchService, 
                         StandardWatchEventKinds.ENTRY_MODIFY,
                         StandardWatchEventKinds.ENTRY_CREATE,
                         StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            logger.error("注册监听路径失败: {}", path, e);
        }
    }
    
    /**
     * 处理文件变更
     * 
     * @param filePath 变更的文件路径
     */
    private static void handleFileChange(Path filePath) {
        String fileName = filePath.toString();
        
        // 查找对应的缓存项
        for (String cachedPath : configCache.keySet()) {
            if (cachedPath.endsWith(fileName)) {
                // 清除对应缓存，触发重新加载
                configCache.remove(cachedPath);
                versionMap.computeIfAbsent(cachedPath, k -> new AtomicLong(0)).incrementAndGet();
                
                logger.info("检测到配置文件变更，已清除缓存: {} (新版本: {})", 
                           cachedPath, versionMap.get(cachedPath).get());
                break;
            }
        }
    }
    
    /**
     * 停止文件监听服务
     */
    public static void stopWatchService() {
        watchEnabled = false;
        if (watchService != null) {
            try {
                watchService.close();
                logger.info("文件监听服务已停止");
            } catch (IOException e) {
                logger.error("停止文件监听服务失败", e);
            }
        }
    }
}