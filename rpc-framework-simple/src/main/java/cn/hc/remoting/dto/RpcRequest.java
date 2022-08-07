package cn.hc.remoting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Rpc 网络请求传输对象
 *
 * @author HCong
 * @create 2022/8/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RpcRequest implements Serializable {
    private static final long serialVersionUID = 1905122041950251207L;
    /**
     * 请求 id
     */
    private String requestID;
    /**
     * 远程接口
     */
    private String interfaceName;
    /**
     * 远程接口下的方法
     */
    private String methodName;
    /**
     * 远程接口下的方法的参数
     */
    private Object[] parameters;
    /**
     * 远程接口下的方法的参数类型
     */
    private Class<?>[] paramTypes;
    /**
     * 版本
     */
    private String version;
    /**
     *
     */
    private String group;

    /**
     * 返回服务的完整名称
     *
     * @return
     */
    public String getRpcServiceName() {
        return this.getInterfaceName() + this.getGroup() + this.getVersion();
    }
}
