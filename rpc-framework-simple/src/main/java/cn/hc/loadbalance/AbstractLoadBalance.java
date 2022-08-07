package cn.hc.loadbalance;

import cn.hc.remoting.dto.RpcRequest;
import cn.hc.utils.CollectionUtil;

import java.util.List;

/**
 * @author HCong
 * @create 2022/8/6
 */
public abstract class AbstractLoadBalance implements LoadBalance {
    /**
     * 若服务列表仅有 1 个或 0 个服务，就无所谓负载均衡
     *
     * @param serviceUrlList 服务地址列表
     * @param rpcRequest     Rpc 请求
     * @return
     */
    @Override
    public String selectServiceAddress(List<String> serviceUrlList, RpcRequest rpcRequest) {
        if (CollectionUtil.isEmpty(serviceUrlList)) {
            return null;
        }
        if (serviceUrlList.size() == 1) {
            return serviceUrlList.get(0);
        }

        // 实际的负载均衡
        return doSelect(serviceUrlList, rpcRequest);
    }

    /**
     * 子类实现不同的负载均衡策略
     *
     * @param serviceAddresses
     * @param rpcRequest
     * @return
     */
    protected abstract String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest);
}
