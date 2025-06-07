package com.knight.server.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 游戏聊天应用启动类
 * 实时聊天服务，支持多频道和敏感词过滤
 * 
 * 功能说明：
 * - 世界聊天频道
 * - 公会聊天频道
 * - 私聊系统
 * - 敏感词过滤
 * - 聊天记录存储
 * - 实时消息推送
 * 
 * 技术选型：Spring Boot + WebSocket + Redis Pub/Sub + MongoDB
 * 
 * @author lx
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.knight.server.chat",
    "com.knight.server.common",
    "com.knight.server.frame"
})
public class ChatApplication {
    
    public static void main(String[] args) {
        // 设置系统属性
        System.setProperty("spring.profiles.active", "chat");
        
        // 启动Spring Boot应用
        SpringApplication.run(ChatApplication.class, args);
    }
}