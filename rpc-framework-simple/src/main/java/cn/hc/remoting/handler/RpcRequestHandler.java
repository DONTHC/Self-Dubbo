package cn.hc.remoting.handler;

import cn.hc.exception.RpcException;
import cn.hc.factory.SingletonFactory;
import cn.hc.provider.ServiceProvider;
import cn.hc.provider.impl.ZkServiceProviderImpl;
import cn.hc.remoting.dto.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * RpcRequest 处理器
 *
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
public class RpcRequestHandler {
    private final ServiceProvider serviceProvider;

    public RpcRequestHandler() {
        this.serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
    }


    /**
     * 处理rpcRequest：调用相应的方法，然后返回该方法
     *
     * @param rpcRequest
     * @return
     */
    public Object handle(RpcRequest rpcRequest) {
        // 根据 RpcServiceName 获取 Service
        Object service = serviceProvider.getService(rpcRequest.getRpcServiceName());
        return invokeTargetMethod(rpcRequest, service);
    }

    /**
     * 基于反射执行 service 中的 method，并返回结果
     *
     * @param rpcRequest
     * @param service
     * @return
     */
    private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) {
        Object result;
        try {
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            result = method.invoke(service, rpcRequest.getParameters());
            log.info("service:[{}] successful invoke method:[{}]", rpcRequest.getInterfaceName(), rpcRequest.getMethodName());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RpcException(e.getMessage(), e);
        }
        return result;
    }
}
