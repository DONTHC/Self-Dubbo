package cn.hc.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务的具体信息
 *
 * @author HCong
 * @create 2022/8/6
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RpcServiceConfig {
    /**
     * 目标 service
     */
    private Object service;

    /**
     * service 版本
     */
    private String version = "";

    /**
     * 当接口存在多个实现时，通过 group 达到去重的效果
     */
    private String group = "";


    public String getRpcServiceName() {
        return this.getServiceName() + this.getGroup() + this.getVersion();
    }

    public String getServiceName() {
        return this.service.getClass().getInterfaces()[0].getCanonicalName();
    }
}
