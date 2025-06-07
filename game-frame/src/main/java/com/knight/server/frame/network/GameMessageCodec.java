package com.knight.server.frame.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.xerial.snappy.Snappy;
import com.knight.server.common.utils.JsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * 游戏消息编解码器
 * 支持JSON序列化和Snappy压缩
 * 
 * 技术选型：JSON + Snappy压缩 + 自定义协议格式
 * 
 * @author lx
 */
public class GameMessageCodec {
    
    private static final Logger logger = LogManager.getLogger(GameMessageCodec.class);
    
    // 协议魔数
    private static final int MAGIC_NUMBER = 0x12345678;
    
    // 压缩阈值（超过此大小才压缩）
    private static final int COMPRESS_THRESHOLD = 1024;
    
    /**
     * 消息编码器
     */
    public static class Encoder extends MessageToByteEncoder<GameMessage> {
        
        @Override
        protected void encode(ChannelHandlerContext ctx, GameMessage msg, ByteBuf out) throws Exception {
            try {
                // 序列化消息体
                byte[] bodyBytes = JsonUtils.toJsonString(msg.getBody()).getBytes("UTF-8");
                
                // 判断是否需要压缩
                boolean compressed = bodyBytes.length > COMPRESS_THRESHOLD;
                if (compressed) {
                    bodyBytes = Snappy.compress(bodyBytes);
                }
                
                // 写入协议头
                out.writeInt(MAGIC_NUMBER);                    // 魔数 (4字节)
                out.writeInt(msg.getMessageType());            // 消息类型 (4字节)
                out.writeLong(msg.getSequence());              // 序列号 (8字节)
                out.writeLong(msg.getTimestamp());             // 时间戳 (8字节)
                out.writeByte(compressed ? 1 : 0);             // 压缩标志 (1字节)
                out.writeInt(bodyBytes.length);                // 消息体长度 (4字节)
                
                // 写入消息体
                out.writeBytes(bodyBytes);
                
            } catch (Exception e) {
                logger.error("消息编码失败", e);
                throw e;
            }
        }
    }
    
    /**
     * 消息解码器
     */
    public static class Decoder extends ByteToMessageDecoder {
        
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            try {
                // 检查是否有足够的字节读取协议头
                if (in.readableBytes() < 29) { // 魔数4 + 消息类型4 + 序列号8 + 时间戳8 + 压缩标志1 + 长度4 = 29字节
                    return;
                }
                
                // 标记读取位置
                in.markReaderIndex();
                
                // 读取魔数
                int magicNumber = in.readInt();
                if (magicNumber != MAGIC_NUMBER) {
                    logger.error("无效的魔数: {}", Integer.toHexString(magicNumber));
                    ctx.close();
                    return;
                }
                
                // 读取协议头
                int messageType = in.readInt();
                long sequence = in.readLong();
                long timestamp = in.readLong();
                boolean compressed = in.readByte() == 1;
                int bodyLength = in.readInt();
                
                // 检查消息体长度是否合理
                if (bodyLength < 0 || bodyLength > 10 * 1024 * 1024) { // 最大10MB
                    logger.error("无效的消息体长度: {}", bodyLength);
                    ctx.close();
                    return;
                }
                
                // 检查是否有足够的字节读取消息体
                if (in.readableBytes() < bodyLength) {
                    // 重置读取位置，等待更多数据
                    in.resetReaderIndex();
                    return;
                }
                
                // 读取消息体
                byte[] bodyBytes = new byte[bodyLength];
                in.readBytes(bodyBytes);
                
                // 解压缩（如果需要）
                if (compressed) {
                    bodyBytes = Snappy.uncompress(bodyBytes);
                }
                
                // 反序列化消息体
                String bodyJson = new String(bodyBytes, "UTF-8");
                Object body = JsonUtils.fromJsonToMap(bodyJson);
                
                // 创建游戏消息对象
                GameMessage message = new GameMessage();
                message.setMessageType(messageType);
                message.setSequence(sequence);
                message.setTimestamp(timestamp);
                message.setBody(body);
                
                out.add(message);
                
            } catch (Exception e) {
                logger.error("消息解码失败", e);
                ctx.close();
            }
        }
    }
    
    /**
     * 游戏消息类
     */
    public static class GameMessage {
        private int messageType;      // 消息类型
        private long sequence;        // 序列号
        private long timestamp;       // 时间戳
        private Object body;          // 消息体
        
        public GameMessage() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public GameMessage(int messageType, Object body) {
            this();
            this.messageType = messageType;
            this.body = body;
        }
        
        // Getters and Setters
        public int getMessageType() { return messageType; }
        public void setMessageType(int messageType) { this.messageType = messageType; }
        
        public long getSequence() { return sequence; }
        public void setSequence(long sequence) { this.sequence = sequence; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public Object getBody() { return body; }
        public void setBody(Object body) { this.body = body; }
        
        @Override
        public String toString() {
            return String.format("GameMessage{type=%d, seq=%d, timestamp=%d, body=%s}",
                               messageType, sequence, timestamp, body);
        }
    }
    
    /**
     * 消息类型常量
     */
    public static class MessageType {
        // 系统消息 (1-100)
        public static final int HEARTBEAT = 1;              // 心跳
        public static final int LOGIN_REQUEST = 2;          // 登录请求
        public static final int LOGIN_RESPONSE = 3;         // 登录响应
        public static final int LOGOUT_REQUEST = 4;         // 登出请求
        public static final int LOGOUT_RESPONSE = 5;        // 登出响应
        
        // 玩家消息 (101-200)
        public static final int PLAYER_INFO = 101;          // 玩家信息
        public static final int PLAYER_MOVE = 102;          // 玩家移动
        public static final int PLAYER_ATTACK = 103;        // 玩家攻击
        
        // 聊天消息 (201-300)
        public static final int CHAT_PRIVATE = 201;         // 私聊
        public static final int CHAT_WORLD = 202;           // 世界聊天
        public static final int CHAT_GUILD = 203;           // 工会聊天
        
        // 背包消息 (301-400)
        public static final int BAG_INFO = 301;             // 背包信息
        public static final int BAG_USE_ITEM = 302;         // 使用物品
        public static final int BAG_SORT = 303;             // 背包整理
        
        // 战斗消息 (401-500)
        public static final int BATTLE_START = 401;         // 战斗开始
        public static final int BATTLE_ACTION = 402;        // 战斗行动
        public static final int BATTLE_RESULT = 403;        // 战斗结果
        
        // 公会消息 (501-600)
        public static final int GUILD_INFO = 501;           // 公会信息
        public static final int GUILD_JOIN = 502;           // 加入公会
        public static final int GUILD_LEAVE = 503;          // 离开公会
        
        // 活动消息 (601-700)
        public static final int ACTIVITY_LIST = 601;        // 活动列表
        public static final int ACTIVITY_JOIN = 602;        // 参加活动
        public static final int ACTIVITY_REWARD = 603;      // 活动奖励
        
        // 错误消息 (9001-9999)
        public static final int ERROR_INVALID_REQUEST = 9001;  // 无效请求
        public static final int ERROR_UNAUTHORIZED = 9002;     // 未授权
        public static final int ERROR_SERVER_BUSY = 9003;      // 服务器繁忙
        public static final int ERROR_INTERNAL = 9999;         // 内部错误
    }
}