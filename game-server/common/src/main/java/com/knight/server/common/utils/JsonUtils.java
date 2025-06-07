package com.knight.server.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * JSON序列化工具类
 * 基于Jackson实现高性能JSON处理
 * 
 * 技术选型：Jackson ObjectMapper + 异常处理
 * 
 * @author lx
 */
public class JsonUtils {
    
    private static final Logger logger = LogManager.getLogger(JsonUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 对象转JSON字符串
     * 
     * @param object 待转换对象
     * @return JSON字符串
     */
    public static String toJsonString(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("对象转JSON失败", e);
            return null;
        }
    }
    
    /**
     * JSON字符串转对象
     * 
     * @param jsonString JSON字符串
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 转换后的对象
     */
    public static <T> T fromJsonString(String jsonString, Class<T> clazz) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(jsonString, clazz);
        } catch (JsonProcessingException e) {
            logger.error("JSON转对象失败: {}", jsonString, e);
            return null;
        }
    }
    
    /**
     * JSON字符串转List
     * 
     * @param jsonString JSON字符串
     * @param clazz 元素类型
     * @param <T> 泛型类型
     * @return List对象
     */
    public static <T> List<T> fromJsonToList(String jsonString, Class<T> clazz) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        
        try {
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            CollectionType listType = typeFactory.constructCollectionType(List.class, clazz);
            return objectMapper.readValue(jsonString, listType);
        } catch (JsonProcessingException e) {
            logger.error("JSON转List失败: {}", jsonString, e);
            return null;
        }
    }
    
    /**
     * JSON字符串转Map
     * 
     * @param jsonString JSON字符串
     * @return Map对象
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJsonToMap(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(jsonString, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("JSON转Map失败: {}", jsonString, e);
            return null;
        }
    }
    
    /**
     * 对象转Map
     * 
     * @param object 待转换对象
     * @return Map对象
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectToMap(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            String jsonString = objectMapper.writeValueAsString(object);
            return objectMapper.readValue(jsonString, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("对象转Map失败", e);
            return null;
        }
    }
    
    /**
     * Map转对象
     * 
     * @param map Map对象
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 转换后的对象
     */
    public static <T> T mapToObject(Map<String, Object> map, Class<T> clazz) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        
        try {
            String jsonString = objectMapper.writeValueAsString(map);
            return objectMapper.readValue(jsonString, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Map转对象失败", e);
            return null;
        }
    }
    
    /**
     * 判断字符串是否为有效JSON
     * 
     * @param jsonString 待检查的字符串
     * @return 是否为有效JSON
     */
    public static boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
    
    /**
     * 格式化JSON字符串（美化输出）
     * 
     * @param jsonString 原始JSON字符串
     * @return 格式化后的JSON字符串
     */
    public static String formatJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return jsonString;
        }
        
        try {
            Object json = objectMapper.readValue(jsonString, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (JsonProcessingException e) {
            logger.error("JSON格式化失败: {}", jsonString, e);
            return jsonString;
        }
    }
}