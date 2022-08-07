package cn.hc.utils;

import java.util.Collection;

/**
 * 集合工具类
 *
 * @author HCong
 * @create 2022/8/6
 */
public class CollectionUtil {
    public static boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }
}
