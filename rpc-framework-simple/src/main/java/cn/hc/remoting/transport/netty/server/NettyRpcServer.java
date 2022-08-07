package cn.hc.remoting.transport.netty.server;

import cn.hc.config.CustomShutdownHook;
import cn.hc.config.RpcServiceConfig;
import cn.hc.factory.SingletonFactory;
import cn.hc.provider.ServiceProvider;
import cn.hc.provider.impl.ZkServiceProviderImpl;
import cn.hc.remoting.transport.netty.codec.RpcMessageDecoder;
import cn.hc.remoting.transport.netty.codec.RpcMessageEncoder;
import cn.hc.utils.RuntimeUtil;
import cn.hc.utils.concurrent.threadpool.ThreadPoolFactoryUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
@Component
public class NettyRpcServer {
    public static final int PORT = 8080;

    private final ServiceProvider serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);

    /**
     * 注册服务至 缓存 和 Zookeeper
     *
     * @param rpcServiceConfig
     */
    public void registerService(RpcServiceConfig rpcServiceConfig) {
        serviceProvider.publishService(rpcServiceConfig);
    }

    @SneakyThrows
    public void start() {
        // 清理之前注册的服务，并关闭所有处理线程
        CustomShutdownHook.getCustomShutdownHook().clearAll();

        String host = InetAddress.getLocalHost().getHostAddress();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        // ?
        DefaultEventExecutorGroup serviceHandlerGroup = new DefaultEventExecutorGroup(
                RuntimeUtil.cpus() * 2,
                ThreadPoolFactoryUtil.createThreadFactory("service-handler-group", false)
        );
        try {
            ChannelFuture channelFuture = new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)           // 表示系统用于临时存放已完成三次握手的请求的队列的最大长度,如果连接建立频繁，服务器处理创建新连接较慢，可以适当调大这个参数
                    .childOption(ChannelOption.TCP_NODELAY, true)    // TCP默认开启了 Nagle 算法，该算法的作用是尽可能的发送大数据快，减少网络传输。TCP_NODELAY 参数的作用就是控制是否启用 Nagle 算法。
                    .childOption(ChannelOption.SO_KEEPALIVE, true)   // 是否开启 TCP 底层心跳机制
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));   // 30 秒之内没有收到客户端请求的话就关闭连接
                            ch.pipeline().addLast(new RpcMessageEncoder());
                            ch.pipeline().addLast(new RpcMessageDecoder());
                            ch.pipeline().addLast(serviceHandlerGroup, new NettyRpcServerHandler());
                        }
                    })
                    .bind(host, PORT).sync();

            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("occur exception when start server:", e);
        } finally {
            log.error("shutdown bossGroup and workerGroup");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            serviceHandlerGroup.shutdownGracefully();
        }
    }
}
