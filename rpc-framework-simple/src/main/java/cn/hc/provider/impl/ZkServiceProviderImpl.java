package cn.hc.provider.impl;


import cn.hc.config.RpcServiceConfig;
import cn.hc.enums.RpcErrorMessageEnum;
import cn.hc.exception.RpcException;
import cn.hc.extension.ExtensionLoader;
import cn.hc.provider.ServiceProvider;
import cn.hc.registry.ServiceRegistry;
import cn.hc.remoting.transport.netty.server.NettyRpcServer;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
public class ZkServiceProviderImpl implements ServiceProvider {
    /**
     * key：rpc 服务名称
     * value：服务对象
     */
    private final Map<String, Object> serviceMap;
    /**
     * 已注册的服务
     */
    private final Set<String> registeredService;
    /**
     * 提供服务注册功能
     */
    private final ServiceRegistry serviceRegistry;

    public ZkServiceProviderImpl() {
        serviceMap = new ConcurrentHashMap<>();
        registeredService = ConcurrentHashMap.newKeySet();
        serviceRegistry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class).getExtension("zk");
    }


    /**
     * 增加服务至 本地缓存
     *
     * @param rpcServiceConfig
     */
    @Override
    public void addService(RpcServiceConfig rpcServiceConfig) {
        String rpcServiceName = rpcServiceConfig.getRpcServiceName();
        if (registeredService.contains(rpcServiceName)) {
            return;
        }

        registeredService.add(rpcServiceName);
        serviceMap.put(rpcServiceName, rpcServiceConfig.getService());
        log.info("Add service: {} and interfaces:{}", rpcServiceName, rpcServiceConfig.getService().getClass().getInterfaces());
    }

    /**
     * 根据 rpcServiceName 获取服务
     *
     * @param rpcServiceName
     * @return
     */
    @Override
    public Object getService(String rpcServiceName) {
        Object service = serviceMap.get(rpcServiceName);
        if (null == service) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND);
        }
        return service;
    }

    /**
     * 注册服务至 Zookeeper
     *
     * @param rpcServiceConfig
     */
    @Override
    public void publishService(RpcServiceConfig rpcServiceConfig) {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            String rpcServiceName = rpcServiceConfig.getRpcServiceName();

            this.addService(rpcServiceConfig);
            serviceRegistry.registerService(rpcServiceName, new InetSocketAddress(host, NettyRpcServer.PORT));
        } catch (UnknownHostException e) {
            log.error("occur exception when getHostAddress", e);
        }
    }
}
