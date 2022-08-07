package cn.hc.remoting.transport.netty.codec;


import cn.hc.compress.Compress;
import cn.hc.enums.CompressTypeEnum;
import cn.hc.enums.SerializationTypeEnum;
import cn.hc.extension.ExtensionLoader;
import cn.hc.remoting.constants.RpcConstants;
import cn.hc.remoting.dto.RpcMessage;
import cn.hc.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>
 * custom protocol decoder
 * <p>
 * <pre>
 *   0     1     2     3     4        5     6     7     8         9          10      11     12  13  14   15 16
 *   +-----+-----+-----+-----+--------+----+----+----+------+-----------+-------+----- --+-----+-----+-------+
 *   |   magic   code        |version | full length         | messageType| codec|compress|    RequestId       |
 *   +-----------------------+--------+---------------------+-----------+-----------+-----------+------------+
 *   |                                                                                                       |
 *   |                                         body                                                          |
 *   |                                                                                                       |
 *   |                                        ... ...                                                        |
 *   +-------------------------------------------------------------------------------------------------------+
 * 4B  magic code（魔法数）   1B version（版本）   4B full length（消息长度）    1B messageType（消息类型）
 * 1B compress（压缩类型） 1B codec（序列化类型）    4B  requestId（请求的Id）
 * body（object类型数据）
 * </pre>
 *
 * @author HCong
 * @create 2022/8/5
 */
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
