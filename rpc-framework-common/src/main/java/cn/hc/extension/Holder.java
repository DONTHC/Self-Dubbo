package cn.hc.extension;

/**
 * @author HCong
 * @create 2022/8/5
 */
public class Holder<T> {

    private volatile T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}