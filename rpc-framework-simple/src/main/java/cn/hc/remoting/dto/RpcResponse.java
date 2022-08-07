package cn.hc.remoting.dto;

import cn.hc.enums.RpcResponseCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author HCong
 * @create 2022/8/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RpcResponse<T> implements Serializable {
    private static final long serialVersionUID = 715745410605631233L;
    /**
     * 请求 id（对应于请求消息）
     */
    private String requestId;
    /**
     * 响应状态码
     */
    private Integer code;
    /**
     * 响应消息
     */
    private String message;
    /**
     * 响应消息体
     */
    private T data;

    /**
     * 成功时的响应消息
     *
     * @param data
     * @param requestId
     * @param <T>
     * @return
     */
    public static <T> RpcResponse<T> success(T data, String requestId) {
        RpcResponse<T> response = new RpcResponse<>();

        response.setRequestId(requestId);
        response.setMessage(RpcResponseCodeEnum.SUCCESS.getMessage());
        response.setCode(RpcResponseCodeEnum.SUCCESS.getCode());
        if (null != data) {
            response.setData(data);
        }
        return response;
    }

    /**
     * 失败时的 响应消息
     *
     * @param rpcResponseCodeEnum
     * @param <T>
     * @return
     */
    public static <T> RpcResponse<T> fail(RpcResponseCodeEnum rpcResponseCodeEnum) {
        RpcResponse<T> response = new RpcResponse<>();

        response.setCode(rpcResponseCodeEnum.getCode());
        response.setMessage(rpcResponseCodeEnum.getMessage());
        return response;
    }
}
