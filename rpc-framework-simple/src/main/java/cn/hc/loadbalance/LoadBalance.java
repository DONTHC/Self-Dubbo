package cn.hc.loadbalance;

import cn.hc.extension.SPI;
import cn.hc.remoting.dto.RpcRequest;

import java.util.List;

/**
 * 负载均衡策略的接口
 *
 * @author HCong
 * @create 2022/8/6
 */
@SPI
public interface LoadBalance {
    /**
     * 从服务地址列表中选择一个服务地址
     *
     * @param serviceUrlList 服务地址列表
     * @param rpcRequest     Rpc 请求
     * @return
     */
    String selectServiceAddress(List<String> serviceUrlList, RpcRequest rpcRequest);
}
