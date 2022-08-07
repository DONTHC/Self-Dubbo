package cn.hc.remoting.constants;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 定义一一些常量
 *
 * @author HCong
 * @create 2022/8/5
 */
public class RpcConstants {
    /**
     * 魔数，用来验证自定义的 Rpc 消息
     */
    public static final byte[] MAGIC_NUMBER = new byte[]{'C', 'o', 'n', 'g'};
    /**
     * 版本
     */
    public static final byte VERSION = 1;
    /**
     * 指定 String 和 byte[] 转换时的编码格式
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final byte TOTAL_LENGTH = 16;

    /**
     * 自定义协议头部长度
     */
    public static final int HEAD_LENGTH = 16;
    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;

    /**
     * 消息类别
     */
    public static final byte REQUEST_TYPE = 1;
    public static final byte RESPONSE_TYPE = 2;
    /**
     * 心跳时的消息类别
     */
    public static final byte HEARTBEAT_REQUEST_TYPE = 3;
    public static final byte HEARTBEAT_RESPONSE_TYPE = 4;
}
