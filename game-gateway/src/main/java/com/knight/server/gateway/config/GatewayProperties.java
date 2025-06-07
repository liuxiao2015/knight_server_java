package com.knight.server.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关配置属性类
 * 统一管理网关服务的所有配置参数
 * 
 * 功能说明：
 * - Netty服务器配置
 * - 性能调优参数
 * - 限流熔断配置
 * - 负载均衡策略
 * - 路由规则配置
 * 
 * 技术选型：Spring Boot Configuration Properties
 * 
 * @author lx
 */
@Component
@ConfigurationProperties(prefix = "knight.gateway")
public class GatewayProperties {
    
    /**
     * Netty配置
     */
    private NettyConfig netty = new NettyConfig();
    
    /**
     * 性能配置
     */
    private PerformanceConfig performance = new PerformanceConfig();
    
    /**
     * 限流配置
     */
    private RateLimiterConfig ratelimiter = new RateLimiterConfig();
    
    /**
     * 负载均衡配置
     */
    private LoadBalancerConfig loadbalancer = new LoadBalancerConfig();
    
    /**
     * 路由配置
     */
    private RouteConfig route = new RouteConfig();
    
    /**
     * 脚本配置
     */
    private ScriptConfig script = new ScriptConfig();
    
    // Getters and Setters
    public NettyConfig getNetty() { return netty; }
    public void setNetty(NettyConfig netty) { this.netty = netty; }
    
    public PerformanceConfig getPerformance() { return performance; }
    public void setPerformance(PerformanceConfig performance) { this.performance = performance; }
    
    public RateLimiterConfig getRatelimiter() { return ratelimiter; }
    public void setRatelimiter(RateLimiterConfig ratelimiter) { this.ratelimiter = ratelimiter; }
    
    public LoadBalancerConfig getLoadbalancer() { return loadbalancer; }
    public void setLoadbalancer(LoadBalancerConfig loadbalancer) { this.loadbalancer = loadbalancer; }
    
    public RouteConfig getRoute() { return route; }
    public void setRoute(RouteConfig route) { this.route = route; }
    
    public ScriptConfig getScript() { return script; }
    public void setScript(ScriptConfig script) { this.script = script; }
    
    /**
     * Netty服务器配置
     */
    public static class NettyConfig {
        private int port = 8090;
        private int bossThreads = 2;
        private int workerThreads = 16;
        private int maxConnections = 100000;
        
        // Getters and Setters
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public int getBossThreads() { return bossThreads; }
        public void setBossThreads(int bossThreads) { this.bossThreads = bossThreads; }
        
        public int getWorkerThreads() { return workerThreads; }
        public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
        
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
    }
    
    /**
     * 性能配置
     */
    public static class PerformanceConfig {
        private int maxRequestsPerSecond = 10000;
        private int maxConnections = 100000;
        private int connectionTimeout = 30000;
        private int readTimeout = 5000;
        private int writeTimeout = 5000;
        
        // Getters and Setters
        public int getMaxRequestsPerSecond() { return maxRequestsPerSecond; }
        public void setMaxRequestsPerSecond(int maxRequestsPerSecond) { this.maxRequestsPerSecond = maxRequestsPerSecond; }
        
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        
        public int getReadTimeout() { return readTimeout; }
        public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
        
        public int getWriteTimeout() { return writeTimeout; }
        public void setWriteTimeout(int writeTimeout) { this.writeTimeout = writeTimeout; }
    }
    
    /**
     * 限流配置
     */
    public static class RateLimiterConfig {
        private boolean enabled = true;
        private int qpsLimit = 10000;
        private int burstCapacity = 20000;
        private int refillPeriodMs = 1000;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getQpsLimit() { return qpsLimit; }
        public void setQpsLimit(int qpsLimit) { this.qpsLimit = qpsLimit; }
        
        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
        
        public int getRefillPeriodMs() { return refillPeriodMs; }
        public void setRefillPeriodMs(int refillPeriodMs) { this.refillPeriodMs = refillPeriodMs; }
    }
    
    /**
     * 负载均衡配置
     */
    public static class LoadBalancerConfig {
        private String strategy = "round-robin";
        private int healthCheckInterval = 30000;
        private int maxRetries = 3;
        
        // Getters and Setters
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        
        public int getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(int healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }
        
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }
    
    /**
     * 路由配置
     */
    public static class RouteConfig {
        private String[] logicServers = {"localhost:9001"};
        private String[] chatServers = {"localhost:9101"};
        private String[] paymentServers = {"localhost:9201"};
        
        // Getters and Setters
        public String[] getLogicServers() { return logicServers; }
        public void setLogicServers(String[] logicServers) { this.logicServers = logicServers; }
        
        public String[] getChatServers() { return chatServers; }
        public void setChatServers(String[] chatServers) { this.chatServers = chatServers; }
        
        public String[] getPaymentServers() { return paymentServers; }
        public void setPaymentServers(String[] paymentServers) { this.paymentServers = paymentServers; }
    }
    
    /**
     * 脚本配置
     */
    public static class ScriptConfig {
        private boolean enabled = true;
        private String path = "/scripts";
        private int reloadInterval = 60000;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public int getReloadInterval() { return reloadInterval; }
        public void setReloadInterval(int reloadInterval) { this.reloadInterval = reloadInterval; }
    }
}