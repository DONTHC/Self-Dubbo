package cn.hc.utils;

/**
 * @author HCong
 * @create 2022/8/6
 */
public class RuntimeUtil {
    /**
     * 获取CPU的核心数
     *
     * @return cpu的核心数
     */
    public static int cpus() {
        return Runtime.getRuntime().availableProcessors();
    }
}
