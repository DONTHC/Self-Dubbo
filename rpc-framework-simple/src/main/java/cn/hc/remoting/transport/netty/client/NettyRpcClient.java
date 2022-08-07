package cn.hc.remoting.transport.netty.client;

import cn.hc.enums.CompressTypeEnum;
import cn.hc.enums.SerializationTypeEnum;
import cn.hc.extension.ExtensionLoader;
import cn.hc.factory.SingletonFactory;
import cn.hc.registry.ServiceDiscovery;
import cn.hc.remoting.constants.RpcConstants;
import cn.hc.remoting.dto.RpcMessage;
import cn.hc.remoting.dto.RpcRequest;
import cn.hc.remoting.dto.RpcResponse;
import cn.hc.remoting.transport.RpcRequestTransport;
import cn.hc.remoting.transport.netty.codec.RpcMessageDecoder;
import cn.hc.remoting.transport.netty.codec.RpcMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author HCong
 * @create 2022/8/5
 */
@Slf4j
public class NettyRpcClient implements RpcRequestTransport {
    /**
     * 提供服务发现功能
     */
    private final ServiceDiscovery serviceDiscovery;
    private final ChannelProvider channelProvider;
    private final UnprocessedRequests unprocessedRequests;

    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;

    public NettyRpcClient() {
        bootstrap = new Bootstrap();
        eventLoopGroup = new NioEventLoopGroup();

        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))                 // 日志
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)    // 连接最大时间
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS));   // 心跳检测，每隔 5 秒发送 Ping 包
                        ch.pipeline().addLast(new RpcMessageEncoder());      // 自定义协议编码器
                        ch.pipeline().addLast(new RpcMessageDecoder());      // 自定义协议解码器
                        ch.pipeline().addLast(new NettyRpcClientHandler());
                    }
                });

        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class).getExtension("zk");
        this.channelProvider = SingletonFactory.getInstance(ChannelProvider.class);
        this.unprocessedRequests = SingletonFactory.getInstance(UnprocessedRequests.class);
    }

    /**
     * 连接服务器并返回对应的 Channel
     *
     * @param inetSocketAddress
     * @return
     */
    @SneakyThrows
    public Channel doConnect(InetSocketAddress inetSocketAddress) {
        CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
        bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("The client has connected [{}] successful!", inetSocketAddress.toString());
                completableFuture.complete(future.channel());
            } else {
                throw new IllegalStateException();

            }
        });
        return completableFuture.get();
    }


    /**
     * 发送 RpcRequest 请求
     *
     * @param rpcRequest
     * @return
     */
    @Override
    public Object sendRpcRequest(RpcRequest rpcRequest) {
        // 构建一个 RpcResponse 响应返回消息
        CompletableFuture<RpcResponse<Object>> resultFuture = new CompletableFuture<>();

        // 根据 interfaceName 在服务中心寻找具体提供服务的Service
        InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcRequest);
        // 获取 Channel
        Channel channel = getChannel(inetSocketAddress);
        if (channel.isActive()) {
            // 保存未处理的请求
            unprocessedRequests.put(rpcRequest.getRequestID(), resultFuture);
            // 构建需要发送的消息
            RpcMessage rpcMessage = RpcMessage.builder()
                    .messageType(RpcConstants.REQUEST_TYPE)
                    .codec(SerializationTypeEnum.HESSIAN.getCode())
                    .compress(CompressTypeEnum.GZIP.getCode())
                    .data(rpcRequest)
                    .build();

            // 发送请求
            channel.writeAndFlush(rpcMessage)
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            log.info("client send message: [{}]", rpcMessage);
                        } else {
                            future.channel().close();
                            // 将返回结果异步写回缓存中的 Map，这样就不用阻塞主线程
                            resultFuture.completeExceptionally(future.cause());
                            log.error("Send failed:", future.cause());
                        }
                    });
        } else {
            throw new IllegalStateException();
        }

        return resultFuture;
    }

    /**
     * 获取 Channel
     *
     * @param inetSocketAddress
     * @return
     */
    public Channel getChannel(InetSocketAddress inetSocketAddress) {
        Channel channel = channelProvider.get(inetSocketAddress);
        if (channel == null) {
            // 如果 Channel 不存在就创建一个
            channel = doConnect(inetSocketAddress);
            channelProvider.set(inetSocketAddress, channel);
        }
        return channel;
    }

    /**
     * 安全关闭
     */
    public void close() {
        eventLoopGroup.shutdownGracefully();
    }
}
