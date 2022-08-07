package cn.hc.proxy;

import cn.hc.config.RpcServiceConfig;
import cn.hc.enums.RpcErrorMessageEnum;
import cn.hc.enums.RpcResponseCodeEnum;
import cn.hc.exception.RpcException;
import cn.hc.remoting.dto.RpcRequest;
import cn.hc.remoting.dto.RpcResponse;
import cn.hc.remoting.transport.RpcRequestTransport;
import cn.hc.remoting.transport.netty.client.NettyRpcClient;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 动态代理类。
 * <p>
 * 当一个动态代理对象调用一个方法时，它实际上调用了下面的调用方法。正是由于动态代理，客户端调用的远程方法就像调用本地方法一样(中间进程是屏蔽的)
 *
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
public class RpcClientProxy implements InvocationHandler {
    /**
     * 接口名称
     */
    private static final String INTERFACE_NAME = "interfaceName";

    /**
     * RpcRequestTransport 向服务器发送请求，可进一步扩展出不同的实现
     */
    private final RpcRequestTransport rpcRequestTransport;

    /**
     * RpcServiceConfig 内部存放远程调用的服务信息
     */
    private final RpcServiceConfig rpcServiceConfig;

    public RpcClientProxy(RpcRequestTransport rpcRequestTransport, RpcServiceConfig rpcServiceConfig) {
        this.rpcRequestTransport = rpcRequestTransport;
        this.rpcServiceConfig = rpcServiceConfig;
    }

    public RpcClientProxy(RpcRequestTransport rpcRequestTransport) {
        this.rpcRequestTransport = rpcRequestTransport;
        rpcServiceConfig = new RpcServiceConfig();
    }

    /**
     * 对外提供获取代理对象的方法
     *
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
    }

    /**
     * 当使用代理对象调用方法时，实际上会调用此方法。
     * 代理对象是通过 getProxy 方法获得的对象。
     *
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log.info("invoked method: [{}]", method.getName());

        // 构建 Rpc 请求消息，准备发送
        RpcRequest rpcRequest = RpcRequest.builder()
                .requestID(UUID.randomUUID().toString())
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameters(args)
                .paramTypes(method.getParameterTypes())
                .version(rpcServiceConfig.getVersion())
                .group(rpcServiceConfig.getGroup())
                .build();

        // 构建 Rpc 响应消息，准备接收
        RpcResponse<Object> rpcResponse = null;
        if (rpcRequestTransport instanceof NettyRpcClient) {
            CompletableFuture<RpcResponse<Object>> completableFuture = (CompletableFuture<RpcResponse<Object>>) rpcRequestTransport.sendRpcRequest(rpcRequest);
            rpcResponse = completableFuture.get();
        }

        this.check(rpcResponse, rpcRequest);

        // 返回消息体
        return rpcResponse.getData();
    }

    /**
     * 检查消息
     *
     * @param rpcResponse
     * @param rpcRequest
     */
    private void check(RpcResponse<Object> rpcResponse, RpcRequest rpcRequest) {
        if (rpcResponse == null) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }

        if (!rpcRequest.getRequestID().equals(rpcResponse.getRequestId())) {
            throw new RpcException(RpcErrorMessageEnum.REQUEST_NOT_MATCH_RESPONSE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }

        if (rpcResponse.getCode() == null || !rpcResponse.getCode().equals(RpcResponseCodeEnum.SUCCESS.getCode())) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
    }
}
