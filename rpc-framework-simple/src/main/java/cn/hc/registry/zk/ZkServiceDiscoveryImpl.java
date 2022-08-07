package cn.hc.registry.zk;

import cn.hc.enums.RpcErrorMessageEnum;
import cn.hc.exception.RpcException;
import cn.hc.extension.ExtensionLoader;
import cn.hc.loadbalance.LoadBalance;
import cn.hc.registry.ServiceDiscovery;
import cn.hc.registry.zk.utils.CuratorUtils;
import cn.hc.remoting.dto.RpcRequest;
import cn.hc.utils.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 从 Zookeeper 上发现服务
 *
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
public class ZkServiceDiscoveryImpl implements ServiceDiscovery {

    private final LoadBalance loadBalance;

    public ZkServiceDiscoveryImpl() {
        this.loadBalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension("loadBalance");
    }

    @Override
    public InetSocketAddress lookupService(RpcRequest rpcRequest) {
        String rpcServiceName = rpcRequest.getRpcServiceName();
        CuratorFramework zkClient = CuratorUtils.getZkClient();

        // 根据服务名称和 Zookeeper 结点获取当前服务的注册列表
        List<String> serviceUrlList = CuratorUtils.getChildrenNodes(zkClient, rpcServiceName);
        // 如果服务不存在
        if (CollectionUtil.isEmpty(serviceUrlList)) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND, rpcServiceName);
        }
        // 负载均衡策略选择 Service 的 Url
        String targetServiceUrl = loadBalance.selectServiceAddress(serviceUrlList, rpcRequest);
        log.info("Successfully found the service address:[{}]", targetServiceUrl);
        String[] socketAddressArray = targetServiceUrl.split(":");
        // 从 Url 中解析出 地址和端口号
        String host = socketAddressArray[0];
        int port = Integer.parseInt(socketAddressArray[1]);

        return new InetSocketAddress(host, port);
    }
}
