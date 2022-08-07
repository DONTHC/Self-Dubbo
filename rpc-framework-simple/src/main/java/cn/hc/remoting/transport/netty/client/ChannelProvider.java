package cn.hc.remoting.transport.netty.client;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储和获取 Channel 对象
 *
 * @author HCong
 * @create 2022/8/5
 */
@Slf4j
public class ChannelProvider {
    private final Map<String, Channel> channelMap;

    public ChannelProvider() {
        channelMap = new ConcurrentHashMap<>();
    }

    /**
     * 从 channelMap 中获取 Chnanel
     *
     * @param inetSocketAddress
     * @return
     */
    public Channel get(InetSocketAddress inetSocketAddress) {
        String key = inetSocketAddress.toString();

        if (channelMap.containsKey(key)) {
            Channel channel = channelMap.get(key);
            if (channel != null && channel.isActive()) {
                return channel;
            } else {
                channelMap.remove(key);
            }
        }
        return null;
    }

    /**
     * 存储 Channel
     *
     * @param inetSocketAddress
     * @param channel
     */
    public void set(InetSocketAddress inetSocketAddress, Channel channel) {
        String key = inetSocketAddress.toString();
        channelMap.put(key, channel);
    }

    /**
     * 移除 Channel
     *
     * @param inetSocketAddress
     */
    public void remove(InetSocketAddress inetSocketAddress) {
        String key = inetSocketAddress.toString();
        if (null != key) {
            channelMap.remove(key);
        }
        log.info("Channel map size :[{}]", channelMap.size());
    }
}
