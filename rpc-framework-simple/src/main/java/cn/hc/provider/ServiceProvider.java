package cn.hc.provider;

import cn.hc.config.RpcServiceConfig;

/**
 * 存储和提供 Service 对象
 *
 * @author HCong
 * @create 2022/8/6
 */
public interface ServiceProvider {
    /**
     * rpc service 与属性相连
     *
     * @param rpcServiceConfig
     */
    void addService(RpcServiceConfig rpcServiceConfig);

    /**
     * 根据 rpcServiceName 获取 service
     *
     * @param rpcServiceName
     * @return service object
     */
    Object getService(String rpcServiceName);

    /**
     * @param rpcServiceConfig
     */
    void publishService(RpcServiceConfig rpcServiceConfig);
}
