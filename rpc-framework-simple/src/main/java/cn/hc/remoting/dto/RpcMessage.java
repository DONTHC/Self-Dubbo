package cn.hc.remoting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author HCong
 * @create 2022/8/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RpcMessage {
    /**
     * rpc 消息类型
     */
    private byte messageType;
    /**
     * 序列化类型
     */
    private byte codec;
    /**
     * 压缩类型
     */
    private byte compress;
    /**
     * 请求 id
     */
    private int requestId;
    /**
     * 请求数据
     */
    private Object data;
}
