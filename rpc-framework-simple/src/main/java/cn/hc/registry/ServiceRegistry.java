package cn.hc.registry;

import cn.hc.extension.SPI;

import java.net.InetSocketAddress;

/**
 * 服务注册
 *
 * @author HCong
 * @create 2022/8/6
 */
@SPI
public interface ServiceRegistry {
    /**
     * 将服务名称和 IP 地址注册至注册中心
     *
     * @param rpcServiceName        完整的服务名称（class name + group + version）
     * @param inetSocketAddress     远程服务地址
     */
    void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress);
}
