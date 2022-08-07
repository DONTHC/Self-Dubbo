package cn.hc.compress;

import cn.hc.extension.SPI;

/**
 * @author HCong
 * @create 2022/8/5
 */
@SPI
public interface Compress {

    /**
     * 压缩
     *
     * @param bytes
     * @return
     */
    byte[] compress(byte[] bytes);


    /**
     * 解压缩
     *
     * @param bytes
     * @return
     */
    byte[] decompress(byte[] bytes);
}
