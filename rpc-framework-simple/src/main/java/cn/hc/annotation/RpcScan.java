package cn.hc.annotation;

import cn.hc.spring.CustomScannerRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 扫描定制注释
 *
 * @author HCong
 * @create 2022/8/6
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Import(CustomScannerRegistrar.class)
public @interface RpcScan {
    String[] basePackage();
}
