package cn.hc.annotation;

import java.lang.annotation.*;

/**
 * RPC 服务注释，标记在服务实现类上（注册服务）
 *
 * @author HCong
 * @create 2022/8/6
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface RpcService {
    /**
     * 服务版本
     *
     * @return
     */
    String version() default "";

    /**
     * 服务group
     *
     * @return
     */
    String group() default "";
}
