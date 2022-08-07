package cn.hc.registry;

import cn.hc.extension.SPI;
import cn.hc.remoting.dto.RpcRequest;

import java.net.InetSocketAddress;

/**
 * 服务发现
 *
 * @author HCong
 * @create 2022/8/5
 */
@SPI
public interface ServiceDiscovery {
    /**
     * 根据 rpcRequest 提供的 rpcServiceName 寻找注册的 service
     *
     * @param rpcRequest Rpc 请求
     * @return
     */
    InetSocketAddress lookupService(RpcRequest rpcRequest);
}
