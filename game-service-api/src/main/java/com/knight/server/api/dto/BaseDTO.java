package com.knight.server.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 基础数据传输对象
 * 所有API DTO的基类，提供公共字段和验证
 * 
 * 功能说明：
 * - 提供统一的时间戳字段
 * - 提供基础的验证注解
 * - 统一JSON序列化格式
 * 
 * 技术选型：Jackson + Validation API
 * 
 * @author lx
 */
public class BaseDTO {
    
    /**
     * 操作时间戳
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * 请求ID，用于链路追踪
     */
    private String requestId;
    
    public BaseDTO() {
        this.timestamp = LocalDateTime.now();
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}