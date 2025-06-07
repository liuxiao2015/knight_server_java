package com.knight.server.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 游戏网关应用启动类
 * 支持100,000+并发连接的高性能API网关
 * 
 * 功能说明：
 * - 智能路由分发
 * - 协议转换和消息聚合  
 * - 限流熔断保护
 * - 会话管理
 * - 负载均衡
 * 
 * 技术选型：Spring Boot + Netty + Virtual Threads
 * 
 * @author lx
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.knight.server.gateway",
    "com.knight.server.common",
    "com.knight.server.frame"
})
public class GatewayApplication {
    
    public static void main(String[] args) {
        // 设置系统属性
        System.setProperty("spring.profiles.active", "gateway");
        
        // 启动Spring Boot应用
        SpringApplication.run(GatewayApplication.class, args);
    }
}