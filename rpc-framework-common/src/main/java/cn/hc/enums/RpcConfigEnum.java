package cn.hc.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author HCong
 * @create 2022/8/6
 */
@AllArgsConstructor
@Getter
public enum RpcConfigEnum {
    RPC_CONFIG_PATH("rpc.properties"),
    ZK_ADDRESS("rpc.zookeeper.address");

    private final String propertyValue;
}
