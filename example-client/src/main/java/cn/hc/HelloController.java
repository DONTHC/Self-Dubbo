package cn.hc;

import cn.hc.annotation.RpcReference;
import org.springframework.stereotype.Component;

/**
 * @author HCong
 * @create 2022/8/6
 */
@Component
public class HelloController {

    @RpcReference(version = "version1.0", group = "test1.0")  // 动态代理的实际在 Bean 的初始化之后
    private HelloService helloService;

    public void test() {
        String hello = helloService.hello(new Hello("China", "Hello"));
        System.out.println(hello);
    }
}
