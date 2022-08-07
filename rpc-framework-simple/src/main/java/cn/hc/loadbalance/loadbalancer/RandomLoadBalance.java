package cn.hc.loadbalance.loadbalancer;

import cn.hc.loadbalance.AbstractLoadBalance;
import cn.hc.remoting.dto.RpcRequest;

import java.util.List;
import java.util.Random;

/**
 * 负载均衡策略：随机选择一个服务
 *
 * @author HCong
 * @create 2022/8/6
 */
public class RandomLoadBalance extends AbstractLoadBalance {
    @Override
    protected String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest) {
        Random random = new Random();
        return serviceAddresses.get(random.nextInt(serviceAddresses.size()));
    }
}
