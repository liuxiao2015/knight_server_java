package com.knight.server.logic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 游戏逻辑应用启动类
 * 核心游戏业务逻辑处理，支持50,000+在线玩家
 * 
 * 功能说明：
 * - 玩家系统（登录、属性、等级）
 * - 背包系统（物品管理、批量操作）
 * - 战斗系统（实时战斗、技能系统）
 * - 邮件系统（个人邮件、系统邮件）
 * - 聊天系统（多频道、敏感词过滤）
 * 
 * 技术选型：Spring Boot + Event-Driven + Virtual Threads + MongoDB + Redis
 * 
 * @author lx
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.knight.server.logic",
    "com.knight.server.common",
    "com.knight.server.frame"
})
public class LogicApplication {
    
    public static void main(String[] args) {
        // 设置系统属性
        System.setProperty("spring.profiles.active", "logic");
        
        // 启动Spring Boot应用
        SpringApplication.run(LogicApplication.class, args);
    }
}