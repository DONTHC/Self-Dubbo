package cn.hc.serialize;

import cn.hc.extension.SPI;

/**
 * 序列化接口，所有序列化类都要实现这个接口
 *
 * @author HCong
 * @create 2022/8/5
 */
@SPI
public interface Serializer {
    /**
     * 序列化
     *
     * @param object
     * @return
     */
    byte[] serialize(Object object);

    /**
     * 反序列化
     *
     * @param bytes
     * @param clazz
     * @param <T>
     * @return
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
