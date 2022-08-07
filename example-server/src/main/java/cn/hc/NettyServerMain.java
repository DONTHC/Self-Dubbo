package cn.hc;

import cn.hc.annotation.RpcScan;
import cn.hc.remoting.transport.netty.server.NettyRpcServer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author HCong
 * @create 2022/8/6
 */
@RpcScan(basePackage = {"cn.hc"})
public class NettyServerMain {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);
        NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer");

        // 启动服务
        nettyRpcServer.start();
    }
}
