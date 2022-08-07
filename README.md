# README

# 1 网络传输模块

## 1.1 定义网络传输对象

网络传输对象包括：**RpcRequest**、**RpcResponse**和**RpcMessage**。关系如下：

![image-20220806194501522](C:\Users\HCong\AppData\Roaming\Typora\typora-user-images\image-20220806194501522.png)

**RpcRequest**和**RpcResponse**作为RpcMessage的成员变量存储，RpcMessage作为网络传输的实体。值得注意的是，messageType等一些常量保存在：

```java
public class RpcConstants {
    /**
     * 魔数，用来验证自定义的 Rpc 消息
     */
    public static final byte[] MAGIC_NUMBER = new byte[]{'C', 'o', 'n', 'g'};
    /**
     * 版本
     */
    public static final byte VERSION = 1;
    /**
     * 指定 String 和 byte[] 转换时的编码格式
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final byte TOTAL_LENGTH = 16;

    /**
     * 自定义协议头部长度
     */
    public static final int HEAD_LENGTH = 16;
    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024;

    /**
     * 消息类别
     */
    public static final byte REQUEST_TYPE = 1;
    public static final byte RESPONSE_TYPE = 2;
    /**
     * 心跳时的消息类别
     */
    public static final byte HEARTBEAT_REQUEST_TYPE = 3;
    public static final byte HEARTBEAT_RESPONSE_TYPE = 4;
}
```

## 1.2 定义基于Netty实现的客户端

先理清客户端发送消息的相关类图：

![image-20220806200356998](C:\Users\HCong\AppData\Roaming\Typora\typora-user-images\image-20220806200356998.png)

RpcRequestTransport是顶层接口，子类实现以不同的方式用来发送 RpcRequest，本次是基于Netty实现的，即NettyRpcClient。NettyRpcClient提供如下服务：

基于Netty构建一个发送消息的架构，即客户端。功能包括：日志、心跳检测、自定义编解码器、RpcMessage请求消息发送、RpcMessage响应消息接收和读取、心跳消息发送和消息异常处理。

### RpcMessage请求消息发送

RpcMessage请求消息发送之前，首先需要连接服务器并返回双方传输数据的通道：

```java
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
```

这里的逻辑就是，在缓存中定义一个存储Channel的Map（反复利用Channel，避免频繁的资源浪费），每次先从缓存中读取Channel，如果存在直接返回，否则基于doConnect方法连接服务器创建一个数据传输的Channel。

Channel的缓存存储在ChannelProvider类中，通过以InetSocketAddress的字符串形式为键保存Channel，也就是说一个IP与服务器仅建立起一个Channel通道进行数据传输。

在得到Channel后，就是发送RpcMessage的方法，包括构建RpcMessage、在注册中心寻找 Service、缓存中保存发送的消息（目的在于确定消息被正确处理，若未被正确处理做另一步打算）、发送请求和构建RpcResponse消息接收结果的流程。如下：

```java
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
```

### RpcMessage响应消息接收

自定义NettyRpcClientHandler来处理服务器返回的消息：

```java
public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    try {
        log.info("client receive msg: [{}]", msg);
        if (msg instanceof RpcMessage) {
            RpcMessage tmp = (RpcMessage) msg;

            byte messageType = tmp.getMessageType();
            if (messageType == RpcConstants.RESPONSE_TYPE) {
                RpcResponse<Object> rpcResponse = (RpcResponse<Object>) tmp.getData();
                unprocessedRequests.complete(rpcResponse);
            } else if (messageType == RpcConstants.HEARTBEAT_RESPONSE_TYPE) {
                log.info("heart [{}]", tmp.getData());
            }
        }
    } finally {
        ReferenceCountUtil.release(msg);
    }
}
```

注意：响应消息返回成功后，删除缓存中 未被处理消息 集合中的记录。当然，在NettyRpcClientHandler还需要处理 心跳检测 和 异常处理。

### 自定义编解码协议

编码器RpcMessageEncoder的实现如下：

```java
@Slf4j
@ChannelHandler.Sharable
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {
    private static final AtomicInteger ATOMIC_INTEGER = new AtomicInteger(0);

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage rpcMessage, ByteBuf out) {
        try {
            // 4 字节魔数
            out.writeBytes(RpcConstants.MAGIC_NUMBER);
            // 1 字节版本
            out.writeByte(RpcConstants.VERSION);
            // 4 字节的消息总长度，后续填充（目前先0填充）
            out.writerIndex(out.writerIndex() + 4);
            // 1 字节消息类型
            byte messageType = rpcMessage.getMessageType();
            out.writeByte(messageType);
            // 1 字节序列化类型
            out.writeByte(rpcMessage.getCodec());
            // 1 字节压缩方式
            out.writeByte(rpcMessage.getCompress());
            // 4 字节消息 Id
            out.writeInt(ATOMIC_INTEGER.getAndIncrement());

            //========================= 上面构建了16字节的头部信息 ==============================
            byte[] bodyBytes = null;
            // 确定 消息头+消息体 的总长度
            // 初始总长度
            int fullLength = RpcConstants.HEAD_LENGTH;
            // 当消息不是 心跳 消息时，对消息进行序列化、压缩等操作
            if (messageType != RpcConstants.HEARTBEAT_REQUEST_TYPE && messageType != RpcConstants.HEARTBEAT_RESPONSE_TYPE) {
                // 获取序列化算法名称
                String codecName = SerializationTypeEnum.getName(rpcMessage.getCodec());
                log.info("codec name: [{}] ", codecName);
                Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(codecName);
                // 序列化
                bodyBytes = serializer.serialize(rpcMessage.getData());

                // 压缩
                String compressName = CompressTypeEnum.getName(rpcMessage.getCompress());
                Compress compress = ExtensionLoader.getExtensionLoader(Compress.class).getExtension(compressName);
                bodyBytes = compress.compress(bodyBytes);

                fullLength += bodyBytes.length;

            }
            // 写入方法体
            if (bodyBytes != null) {
                out.writeBytes(bodyBytes);
            }
            // 写入消息的总长度
            int writerIndex = out.writerIndex();
            out.writerIndex(writerIndex - fullLength + RpcConstants.MAGIC_NUMBER.length + 1);
            // 注意，此处将总长度写入至之前的流出的空位之上
            out.writeInt(fullLength);
            out.writerIndex(writerIndex);
        } catch (Exception e) {
            log.error("Encode request error!", e);
        }
    }
}
```

解码器RpcMessageDecoder实现如下：

```java
@Slf4j
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {
    public RpcMessageDecoder() {
        // lengthFieldOffset: magic code is 4B, and version is 1B, and then full length. so value is 5
        // lengthFieldLength: full length is 4B. so value is 4
        // lengthAdjustment: full length include all data and read 9 bytes before, so the left length is (fullLength-9). so values is -9
        // initialBytesToStrip: we will check magic code and version manually, so do not strip any bytes. so values is 0
        this(RpcConstants.MAX_FRAME_LENGTH, 5, 4, -9, 0);
    }

    /**
     * @param maxFrameLength      Maximum frame length. It decide the maximum length of data that can be received.
     *                            If it exceeds, the data will be discarded.
     * @param lengthFieldOffset   Length field offset. The length field is the one that skips the specified length of byte.
     * @param lengthFieldLength   The number of bytes in the length field.
     * @param lengthAdjustment    The compensation value to add to the value of the length field
     * @param initialBytesToStrip Number of bytes skipped.
     *                            If you need to receive all of the header+body data, this value is 0
     *                            if you only want to receive the body data, then you need to skip the number of bytes consumed by the header.
     */
    public RpcMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
                             int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    /**
     * 解码器
     *
     * @param ctx
     * @param in
     * @return
     * @throws Exception
     */
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        Object decoded = super.decode(ctx, in);

        if (decoded instanceof ByteBuf) {
            ByteBuf frame = (ByteBuf) decoded;
            if (frame.readableBytes() >= RpcConstants.TOTAL_LENGTH) {
                try {
                    return decodeFrame(frame);
                } catch (Exception e) {
                    log.error("Decode frame error!", e);
                    throw e;
                } finally {
                    frame.release();
                }
            }

        }
        return decoded;
    }

    /**
     * 根据自定义协议将 byte[] 解析为对象
     *
     * @param in
     * @return
     */
    private Object decodeFrame(ByteBuf in) {
        // 验证魔数
        checkMagicNumber(in);
        // 验证版本
        checkVersion(in);
        // 读取消息总长度（头部 + 压缩后的消息体）
        int fullLength = in.readInt();
        // 读取 1 字节消息类型
        byte messageType = in.readByte();
        // 读取 1 字节序列化类型
        byte codecType = in.readByte();
        // 读取 1 字节压缩方式
        byte compressType = in.readByte();
        // 读取 4 字节消息 Id
        int requestId = in.readInt();

        //===========================16字节的头部已读取完毕==============================
        // 重构请求消息
        RpcMessage rpcMessage = RpcMessage.builder()
                .codec(codecType)
                .requestId(requestId)
                .messageType(messageType)
                .build();
        if (messageType == RpcConstants.HEARTBEAT_REQUEST_TYPE) {
            rpcMessage.setData(RpcConstants.PING);
            return rpcMessage;
        }
        if (messageType == RpcConstants.HEARTBEAT_RESPONSE_TYPE) {
            rpcMessage.setData(RpcConstants.PONG);
            return rpcMessage;
        }
        // 消息体长度（压缩后）
        int bodyLength = fullLength - RpcConstants.HEAD_LENGTH;
        if (bodyLength > 0) {
            byte[] bs = new byte[bodyLength];
            in.readBytes(bs);

            // 解压缩
            String compressName = CompressTypeEnum.getName(compressType);
            Compress compress = ExtensionLoader.getExtensionLoader(Compress.class).getExtension(compressName);
            bs = compress.decompress(bs);

            // 反序列化
            String codecName = SerializationTypeEnum.getName(rpcMessage.getCodec());
            log.info("codec name: [{}] ", codecName);
            Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(codecName);

            if (messageType == RpcConstants.REQUEST_TYPE) {
                RpcRequest tmpValue = serializer.deserialize(bs, RpcRequest.class);
                rpcMessage.setData(tmpValue);
            } else {
                RpcResponse tmpValue = serializer.deserialize(bs, RpcResponse.class);
                rpcMessage.setData(tmpValue);
            }
        }
        return rpcMessage;

    }

    /**
     * 验证版本
     *
     * @param in
     */
    private void checkVersion(ByteBuf in) {
        byte version = in.readByte();

        if (version != RpcConstants.VERSION) {
            throw new RuntimeException("version isn't compatible" + version);
        }
    }

    /**
     * 验证魔数
     *
     * @param in
     */
    private void checkMagicNumber(ByteBuf in) {
        int len = RpcConstants.MAGIC_NUMBER.length;

        byte[] tmp = new byte[len];
        in.readBytes(tmp);
        for (int i = 0; i < len; i++) {
            if (tmp[i] != RpcConstants.MAGIC_NUMBER[i]) {
                throw new IllegalArgumentException("Unknown magic code: " + Arrays.toString(tmp));
            }
        }
    }
}
```

注意，解码器继承LengthFieldBasedFrameDecoder，在设定了恰当的参数后，避免了黏包和半包问题。

### 序列化方法

在自定义编解码器时基于Hessian实现对象的序列化：

![image-20220806204840986](C:\Users\HCong\AppData\Roaming\Typora\typora-user-images\image-20220806204840986.png)

注意，实现Serializer可以进一步扩展序列化方法。在自定义编解码过程中，通过如下方式指定数据的序列化方法：

```java
Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(codecName);
```

SPI机制，可通过学习 Dubbo 源码进一步学习。

## 1.3 定义基于Netty实现的服务器

先理清服务器接收RpcMessage消息的相关类图：

![image-20220806215937286](C:\Users\HCong\AppData\Roaming\Typora\typora-user-images\image-20220806215937286.png)

服务器提供的功能包括：日志、心跳检测、自定义编解码器、RpcMessage请求消息的接受、RpcMessage响应消息的发送、异常消息处理和服务注册功能。

基于Netty构建的服务器架构如下：

```java
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
```

### RpcMessage消息处理

自定义NettyRpcServerHandler处理RpcMessage消息的接受和发送：

```java
/**
 * 处理发送给服务器的 Rpc 消息，并响应回
 *
 * @param ctx
 * @param msg
 * @throws Exception
 */
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    try {
        if (msg instanceof RpcMessage) {
            log.info("server receive msg: [{}] ", msg);
            // 获取消息类型
            byte messageType = ((RpcMessage) msg).getMessageType();

            // 构建响应 Rpc 消息
            RpcMessage rpcMessage = new RpcMessage();
            rpcMessage.setCodec(SerializationTypeEnum.HESSIAN.getCode());
            rpcMessage.setCompress(CompressTypeEnum.GZIP.getCode());

            // 如果是心跳的 Ping 信息，封装 Pong 消息返回
            if (messageType == RpcConstants.HEARTBEAT_REQUEST_TYPE) {
                rpcMessage.setMessageType(RpcConstants.HEARTBEAT_RESPONSE_TYPE);
                rpcMessage.setData(RpcConstants.PONG);
            }
            // 否则，基于 RpcRequestHandler 处理后，返回其结果
            else {
                RpcRequest rpcRequest = (RpcRequest) ((RpcMessage) msg).getData();
                // 处理消息
                Object result = rpcRequestHandler.handle(rpcRequest);
                log.info(String.format("server get result: %s", result.toString()));
                rpcMessage.setMessageType(RpcConstants.RESPONSE_TYPE);

                if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                    RpcResponse<Object> rpcResponse = RpcResponse.success(result, rpcRequest.getRequestID());
                    rpcMessage.setData(rpcResponse);
                } else {
                    RpcResponse<Object> rpcResponse = RpcResponse.fail(RpcResponseCodeEnum.FAIL);
                    rpcMessage.setData(rpcResponse);
                    log.error("not writable now, message dropped");
                }
            }

            // 返回消息 ?
            ctx.writeAndFlush(rpcMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    } finally {
        // 确保释放ByteBuf，否则可能会出现内存泄漏
        ReferenceCountUtil.release(msg);
    }
}
```

注意：在取出RpcMessage中的Data、interface等参数后，基于RpcRequestHandler反射运行该接口的实现并返回结果。

# 2 注册中心模块

注册中心为客户端提供服务发现、为服务器提供服务注册的功能：

![image-20220806214132750](C:\Users\HCong\AppData\Roaming\Typora\typora-user-images\image-20220806214132750.png)

基于 Zookeeper 实现服务的注册和发现，注意在服务发现的同时可运用不同的负载均衡的策略实现服务的获取。下面分两步：

- 将服务注册功能嵌入至服务器，即在服务器的启动过程中将服务注册至Zookeeper注册中心（服务注册功能是通过注解实现的）。
- 讲服务发现功能嵌入至客户端，即在客户端发送RpcMessage时，通过注册中心寻找目标服务的IP和PORT。

# 3 其他模块

## 3.1 客户端代理

客户端需要根据调用的接口构建RpcMessage请求消息发送至服务器，因此可以采取动态代理来屏蔽复杂的网络传输细节。即仅需要调用接口方法即可，后续的消息封装和发送皆由RpcClientProxy代理对象进行处理：

```java
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
```

## 3.2 基于Spring注解注册和消费服务

注意客户端代理后还并未生效，后续需要通过注解的方式将代理的对象在 Bean 生命周期的初始化之后将其注入。下面理清基于Spring注解注册和消费服务的功能。

![image-20220806233024403](C:\Users\HCong\AppData\Roaming\Typora\typora-user-images\image-20220806233024403.png)

定义RpcScan接口负责扫描注有RpcService和RpcService注解的类加入Spring容器。那么注有RpcReference注解的类怎么办呢？通过添加 Bean 后处理器，将 RpcClientProxy 代理过后的对象注入， Bean 后处理器如下：

```java
/**
 * 扫描和筛选指定的注释在创建bean之前调用该方法，以查看类是否被注释
 *
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
@Component
public class SpringBeanPostProcessor implements BeanPostProcessor {
    private final ServiceProvider serviceProvider;
    private final RpcRequestTransport rpcClient;

    public SpringBeanPostProcessor() {
        this.serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
        this.rpcClient = ExtensionLoader.getExtensionLoader(RpcRequestTransport.class).getExtension("netty");
    }

    /**
     * Bean 初始化之前进行处理（Bean 后处理器）
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @SneakyThrows
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 判断该类是否标注了 RpcService 注解
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
            // 获取 RpcService 注解
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
            // 构建 RpcServiceConfig 对象，从注解中解析出 version 和 group
            RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                    .group(rpcService.group())
                    .version(rpcService.version())
                    .service(bean).build();
            // 服务注册，发生在 Bean 初始化之前
            serviceProvider.publishService(rpcServiceConfig);
        }
        return bean;
    }

    /**
     * 初始化后执行的后处理器
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 获取 Bean 的 Class
        Class<?> targetClass = bean.getClass();
        // 获取 Bean 的 成员属性
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            // 处理标注了 RpcReference 的对象
            RpcReference rpcReference = declaredField.getAnnotation(RpcReference.class);

            if (rpcReference != null) {
                // 构建 RpcServiceConfig 对象，从注解中解析出 version 和 group
                RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                        .group(rpcReference.group())
                        .version(rpcReference.version()).build();
                // 创建当前对象的代理对象
                RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceConfig);
                Object clientProxy = rpcClientProxy.getProxy(declaredField.getType());
                declaredField.setAccessible(true);
                try {
                    // 将代理对象注入
                    declaredField.set(bean, clientProxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }
        return bean;
    }
}
```

注意，服务注册发生在 Bean 初始化之前，即`serviceProvider.publishService(rpcServiceConfig);`。

代理对象的注入发生在 Bean 初始化之后，即`declaredField.set(bean, clientProxy);`

至此，自定义 Dubbo 实现完成，编写测试类测试如下：

- 启动服务器：

```java
@RpcScan(basePackage = {"cn.hc"})
public class NettyServerMain {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);
        NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer");

        // 启动服务
        nettyRpcServer.start();
    }
}
```

- 启动客户端：

```java
@Component
public class HelloController {

    @RpcReference(version = "version1.0", group = "test1.0")  // 动态代理的实际在 Bean 的初始化之后
    private HelloService helloService;

    public void test() {
        String hello = helloService.hello(new Hello("China", "Hello"));
        System.out.println(hello);
    }
}
```

```java
@RpcScan(basePackage = "cn.hc")
public class NettyClientMain {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyClientMain.class);
        HelloController helloController = (HelloController) applicationContext.getBean("helloController");
        helloController.test();
    }
}
```

# 4 设计模式

 整个自定义 Dubbo 实现过程过，一些类都是只需要保持全局的单例模式即可，通过如下工厂类获取单例：

```java
/**
 * 获取单例对象的工厂类
 *
 * @author HCong
 * @create 2022/8/5
 */
public class SingletonFactory {
    private static final Map<String, Object> OBJECT_MAP = new ConcurrentHashMap<>();

    public static <T> T getInstance(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException();
        }

        String key = clazz.toString();
        // 如果缓存中存在实例，直接返回
        if (OBJECT_MAP.containsKey(key)) {
            return clazz.cast(OBJECT_MAP.get(key));
        }
        // 否则，基于反射创建实例加入 Map，并返回
        else {
            return clazz.cast(OBJECT_MAP.computeIfAbsent(key, k -> {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }));
        }
    }
}
```

# 5 SPI机制

![img](https://img.jbzj.com/file_images/article/202111/2021112310470213.png)

SPI全称为Service Provider Interface，是一种服务发现机制。SPI的本质是将接口实现类的全限定名配置在文件中，并由服务加载器读取配置文件，加载实现类。这样可以在运行时，动态为接口替换实现类。正因此特性，我们可以很容易的通过SPI机制为我们的程序提供拓展功能。

## 5.1 Java SPI

### Java SPI Demo

首先，定义一个接口及其两个实现类：

```java
public interface Robot {
    void sayHello();
}
```

```java
public class OptimusPrime implements Robot {
    @Override
    public void sayHello() {
        System.out.println("Hello, I am Optimus Prime.");
    }
}
```

```java
public class Bumblebee implements Robot {
    @Override
    public void sayHello() {
        System.out.println("Hello, I am Bumblebee.");
    }
}
```

然后，在资源的META-INF/services包下放置一个接口同名文件（全限定类名），内容就是接口的实现类：

```tex
cn.hc.javaspi.impl.OptimusPrime
cn.hc.javaspi.impl.Bumblebee
```

测试：

```java
public class TestJavaSPI {
    public static void main(String[] args) {
        ServiceLoader<Robot> serviceLoader = ServiceLoader.load(Robot.class);
        System.out.println(serviceLoader);

        for (Robot robot : serviceLoader) {
            robot.sayHello();
        }
    }
}
```

```
java.util.ServiceLoader[cn.hc.javaspi.Robot]
Hello, I am Optimus Prime.
Hello, I am Bumblebee.
```

### Java SPI 缺点

- 不能按需加载。从上面的测试来看，Java SPI机制会将该接口的实现全部加载，但是在实际业务中会造成资源的浪费。
- 延迟加载不够优化。虽然 ServiceLoader 做了延迟加载，但是只能通过遍历的方式全部获取。如果其中某些实现类很耗时，而且你也不需要加载它，那么就形成了资源浪费获取某个实现类的方式不够灵活，只能通过迭代器的形式获取。

### Java SPI 源码

建议阅读此[文章](https://blog.csdn.net/m0_45016797/article/details/124188738)。

## 5.2 Dubbo SPI

### Dubbo SPI Demo

接口与实现类同 Java SPI，需要在META-INF/dubbo文件夹下进行如下配置：

```
optimusPrime = cn.hc.javaspi.impl.OptimusPrime
bumblebee = cn.hc.javaspi.impl.Bumblebee
```

测试：

```java
public class TestDubboSPI {
    public static void main(String[] args) {
        ExtensionLoader<Robot> extensionLoader = ExtensionLoader.getExtensionLoader(Robot.class);

        Robot optimusPrime = extensionLoader.getExtension("optimusPrime");
        Robot bumblebee = extensionLoader.getExtension("bumblebee");
        optimusPrime.sayHello();
        bumblebee.sayHello();
    }
}
```

### Dubbo SPI 源码

getExtensionLoader方法：

```java
public <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
    // 检查保存 ExtensionLoader 的 extensionLoadersMap 是否还存在
    checkDestroyed();
    if (type == null) {
        throw new IllegalArgumentException("Extension type == null");
    }
    // 不是接口的抛出异常
    if (!type.isInterface()) {
        throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
    }
    // 没有标注 @SPI 注解的抛出异常
    if (!withExtensionAnnotation(type)) {
        throw new IllegalArgumentException("Extension type (" + type +
            ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
    }

    // 1. 从缓存中获取
    ExtensionLoader<T> loader = (ExtensionLoader<T>) extensionLoadersMap.get(type);

    ExtensionScope scope = extensionScopeMap.get(type);
    if (scope == null) {
        SPI annotation = type.getAnnotation(SPI.class);
        scope = annotation.scope();
        extensionScopeMap.put(type, scope);
    }

    if (loader == null && scope == ExtensionScope.SELF) {
        // create an instance in self scope
        loader = createExtensionLoader0(type);
    }

    // 2. 如果缓存中不存在，找其父类加载器
    if (loader == null) {
        if (this.parent != null) {
            loader = this.parent.getExtensionLoader(type);
        }
    }

    // 3. create it
    if (loader == null) {
        loader = createExtensionLoader(type);
    }

    return loader;
}
```



getExtension方法：

```java
@SuppressWarnings("unchecked")
public T getExtension(String name, boolean wrap) {
    checkDestroyed();
    if (StringUtils.isEmpty(name)) {
        throw new IllegalArgumentException("Extension name == null");
    }
    // 获取默认扩展实现类
    if ("true".equals(name)) {
        return getDefaultExtension();
    }
    String cacheKey = name;
    if (!wrap) {
        cacheKey += "_origin";
    }
    
    // 为什么要用一个holder来包装？
    // 思考： 要加锁，如果不用holder来包装的话锁的对象不好弄。
    // 因为一开始创建都是null会导致全都拿null来当锁。就单线程了。
    // 如果直接拿name当锁不优雅
    final Holder<Object> holder = getOrCreateHolder(cacheKey);
    Object instance = holder.get();
    
    // 双层锁创建目标单例对象
    if (instance == null) {
        synchronized (holder) {
            instance = holder.get();
            if (instance == null) {
                instance = createExtension(name, wrap);
                // 将对象设置到Holder
                holder.set(instance);
            }
        }
    }
    return (T) instance;
}
```

createExtension方法：

```java
private T createExtension(String name) {
    // 从配置文件中加载所有的拓展类，可得到“配置项名称”到“配置类”的映射关系表
    Class<?> clazz = getExtensionClasses().get(name);
    if (clazz == null) {
        throw findException(name);
    }
    try {
        T instance = (T) EXTENSION_INSTANCES.get(clazz);
        if (instance == null) {
            // 通过反射创建实例
            EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
            instance = (T) EXTENSION_INSTANCES.get(clazz);
        }
        // 向实例中注入依赖
        injectExtension(instance);
        Set<Class<?>> wrapperClasses = cachedWrapperClasses;
        if (wrapperClasses != null && !wrapperClasses.isEmpty()) {
            // 循环创建 Wrapper 实例
            for (Class<?> wrapperClass : wrapperClasses) {
                // 将当前 instance 作为参数传给 Wrapper 的构造方法，并通过反射创建 Wrapper 实例。
                // 然后向 Wrapper 实例中注入依赖，最后将 Wrapper 实例再次赋值给 instance 变量
                instance = injectExtension(
                    (T) wrapperClass.getConstructor(type).newInstance(instance));
            }
        }
        return instance;
    } catch (Throwable t) {
        throw new IllegalStateException("...");
    }
}
```

- 通过getExtensionClasses获取所有的拓展类
- 通过反射创建拓展对象
- 向拓展对象中注入依赖
- 将拓展对象包裹在相应的wrapper对象中

具体见Dubbo源码。

# 进一步学习知识点



- Dubbo IOC
- 负载均衡
- Zookeeper基本使用