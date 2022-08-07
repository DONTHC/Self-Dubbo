package cn.hc;

import cn.hc.annotation.RpcScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 测试 Rpc 调用
 *
 * @author HCong
 * @create 2022/8/6
 */
@RpcScan(basePackage = "cn.hc")
public class NettyClientMain {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyClientMain.class);
        HelloController helloController = (HelloController) applicationContext.getBean("helloController");
        helloController.test();
    }
}
