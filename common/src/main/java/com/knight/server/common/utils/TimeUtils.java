package com.knight.server.common.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 时间工具类
 * 支持多时区处理和高性能时间操作
 * 技术选型：Java 8+ Time API + 时区缓存优化
 * 
 * @author lx
 */
public class TimeUtils {
    
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    // 时区缓存，避免重复创建
    private static final ConcurrentMap<String, ZoneId> ZONE_CACHE = new ConcurrentHashMap<>();
    
    // 常用时区
    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    public static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    public static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    public static final ZoneId LONDON = ZoneId.of("Europe/London");
    
    /**
     * 获取当前时间戳（秒）
     * 
     * @return 当前时间戳
     */
    public static long currentTimestamp() {
        return System.currentTimeMillis() / 1000;
    }
    
    /**
     * 获取当前时间戳（毫秒）
     * 
     * @return 当前时间戳（毫秒）
     */
    public static long currentMillis() {
        return System.currentTimeMillis();
    }
    
    /**
     * 获取当前时间
     * 
     * @return 当前LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
    
    /**
     * 获取指定时区的当前时间
     * 
     * @param zoneId 时区ID
     * @return 指定时区的当前时间
     */
    public static LocalDateTime now(String zoneId) {
        return LocalDateTime.now(getZoneId(zoneId));
    }
    
    /**
     * 获取指定时区的当前时间
     * 
     * @param zoneId 时区
     * @return 指定时区的当前时间
     */
    public static LocalDateTime now(ZoneId zoneId) {
        return LocalDateTime.now(zoneId);
    }
    
    /**
     * 时间戳转LocalDateTime
     * 
     * @param timestamp 时间戳（秒）
     * @return LocalDateTime
     */
    public static LocalDateTime fromTimestamp(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    }
    
    /**
     * 时间戳转LocalDateTime（指定时区）
     * 
     * @param timestamp 时间戳（秒）
     * @param zoneId 时区
     * @return LocalDateTime
     */
    public static LocalDateTime fromTimestamp(long timestamp, ZoneId zoneId) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), zoneId);
    }
    
    /**
     * LocalDateTime转时间戳
     * 
     * @param dateTime LocalDateTime
     * @return 时间戳（秒）
     */
    public static long toTimestamp(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
    
    /**
     * LocalDateTime转时间戳（指定时区）
     * 
     * @param dateTime LocalDateTime
     * @param zoneId 时区
     * @return 时间戳（秒）
     */
    public static long toTimestamp(LocalDateTime dateTime, ZoneId zoneId) {
        return dateTime.atZone(zoneId).toEpochSecond();
    }
    
    /**
     * 格式化时间
     * 
     * @param dateTime 时间
     * @return 格式化后的时间字符串
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime.format(DEFAULT_FORMATTER);
    }
    
    /**
     * 格式化时间（自定义格式）
     * 
     * @param dateTime 时间
     * @param pattern 格式模式
     * @return 格式化后的时间字符串
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * 格式化日期
     * 
     * @param date 日期
     * @return 格式化后的日期字符串
     */
    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * 格式化时间
     * 
     * @param time 时间
     * @return 格式化后的时间字符串
     */
    public static String formatTime(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }
    
    /**
     * 生成时间戳格式的字符串
     * 
     * @param dateTime 时间
     * @return 时间戳格式字符串
     */
    public static String timestampFormat(LocalDateTime dateTime) {
        return dateTime.format(TIMESTAMP_FORMATTER);
    }
    
    /**
     * 解析时间字符串
     * 
     * @param timeStr 时间字符串
     * @return LocalDateTime
     */
    public static LocalDateTime parse(String timeStr) {
        return LocalDateTime.parse(timeStr, DEFAULT_FORMATTER);
    }
    
    /**
     * 解析时间字符串（自定义格式）
     * 
     * @param timeStr 时间字符串
     * @param pattern 格式模式
     * @return LocalDateTime
     */
    public static LocalDateTime parse(String timeStr, String pattern) {
        return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * 计算时间差（秒）
     * 
     * @param start 开始时间
     * @param end 结束时间
     * @return 时间差（秒）
     */
    public static long diffSeconds(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).getSeconds();
    }
    
    /**
     * 计算时间差（毫秒）
     * 
     * @param start 开始时间
     * @param end 结束时间
     * @return 时间差（毫秒）
     */
    public static long diffMillis(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toMillis();
    }
    
    /**
     * 计算天数差
     * 
     * @param start 开始日期
     * @param end 结束日期
     * @return 天数差
     */
    public static long diffDays(LocalDate start, LocalDate end) {
        return end.toEpochDay() - start.toEpochDay();
    }
    
    /**
     * 获取今天的开始时间
     * 
     * @return 今天00:00:00
     */
    public static LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }
    
    /**
     * 获取今天的结束时间
     * 
     * @return 今天23:59:59
     */
    public static LocalDateTime todayEnd() {
        return LocalDate.now().atTime(23, 59, 59);
    }
    
    /**
     * 判断是否是同一天
     * 
     * @param date1 日期1
     * @param date2 日期2
     * @return 是否同一天
     */
    public static boolean isSameDay(LocalDateTime date1, LocalDateTime date2) {
        return date1.toLocalDate().equals(date2.toLocalDate());
    }
    
    /**
     * 获取时区ID（带缓存）
     * 
     * @param zoneId 时区ID字符串
     * @return ZoneId对象
     */
    private static ZoneId getZoneId(String zoneId) {
        return ZONE_CACHE.computeIfAbsent(zoneId, ZoneId::of);
    }
    
    /**
     * 时区转换
     * 
     * @param dateTime 原时间
     * @param fromZone 原时区
     * @param toZone 目标时区
     * @return 转换后的时间
     */
    public static LocalDateTime convertZone(LocalDateTime dateTime, ZoneId fromZone, ZoneId toZone) {
        return dateTime.atZone(fromZone).withZoneSameInstant(toZone).toLocalDateTime();
    }
    
    /**
     * 获取当前周的开始时间（周一00:00:00）
     * 
     * @return 本周开始时间
     */
    public static LocalDateTime weekStart() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        return monday.atStartOfDay();
    }
    
    /**
     * 获取当前月的开始时间
     * 
     * @return 本月开始时间
     */
    public static LocalDateTime monthStart() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.with(TemporalAdjusters.firstDayOfMonth());
        return firstDay.atStartOfDay();
    }
}