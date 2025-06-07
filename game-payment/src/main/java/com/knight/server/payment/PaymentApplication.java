package com.knight.server.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 游戏支付应用启动类
 * 安全的支付处理和订单管理服务
 * 
 * 功能说明：
 * - 支付订单创建和管理
 * - 第三方支付平台对接（支付宝、微信、苹果等）
 * - 支付回调处理
 * - 订单状态跟踪
 * - 支付数据统计和分析
 * - 退款处理
 * 
 * 技术选型：Spring Boot + Spring Security + MongoDB + Redis
 * 
 * @author lx
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.knight.server.payment",
    "com.knight.server.common",
    "com.knight.server.frame"
})
public class PaymentApplication {
    
    public static void main(String[] args) {
        // 设置系统属性
        System.setProperty("spring.profiles.active", "payment");
        
        // 启动Spring Boot应用
        SpringApplication.run(PaymentApplication.class, args);
    }
}