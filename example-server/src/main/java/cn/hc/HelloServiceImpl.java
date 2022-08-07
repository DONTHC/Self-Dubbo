package cn.hc;

import cn.hc.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;

/**
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
@RpcService(version = "version1.0", group = "test1.0")
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(Hello hello) {
        log.info("HelloServiceImpl收到: {}.", hello.getMessage());

        String result = hello.getMessage() + hello.getDescription();
        return result;
    }
}
