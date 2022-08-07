package cn.hc.annotation;

import java.lang.annotation.*;

/**
 * RPC引用注释，自动装配服务实现类（消费服务）
 *
 * @author HCong
 * @create 2022/8/6
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Inherited
public @interface RpcReference {
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
