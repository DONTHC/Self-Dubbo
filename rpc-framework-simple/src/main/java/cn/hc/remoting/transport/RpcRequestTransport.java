package cn.hc.remoting.transport;

import cn.hc.extension.SPI;
import cn.hc.remoting.dto.RpcRequest;

/**
 * 顶层接口，子类实现以不同的方式用来发送 RpcRequest
 *
 * @author HCong
 * @create 2022/8/5
 */
@SPI
public interface RpcRequestTransport {

    /**
     * 发送 RpcRequest 至 Server，并返回结果
     *
     * @param rpcRequest
     * @return
     */
    Object sendRpcRequest(RpcRequest rpcRequest);
}
