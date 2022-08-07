package cn.hc.spring;

import cn.hc.annotation.RpcReference;
import cn.hc.annotation.RpcService;
import cn.hc.config.RpcServiceConfig;
import cn.hc.extension.ExtensionLoader;
import cn.hc.factory.SingletonFactory;
import cn.hc.provider.ServiceProvider;
import cn.hc.provider.impl.ZkServiceProviderImpl;
import cn.hc.proxy.RpcClientProxy;
import cn.hc.remoting.transport.RpcRequestTransport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * 扫描和筛选指定的注释在创建bean之前调用该方法，以查看类是否被注释
 *
 * @author HCong
 * @create 2022/8/6
 */
@Slf4j
@Component
public class SpringBeanPostProcessor implements BeanPostProcessor {
    private final ServiceProvider serviceProvider;
    private final RpcRequestTransport rpcClient;

    public SpringBeanPostProcessor() {
        this.serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
        this.rpcClient = ExtensionLoader.getExtensionLoader(RpcRequestTransport.class).getExtension("netty");
    }

    /**
     * Bean 初始化之前进行处理（Bean 后处理器）
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @SneakyThrows
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 判断该类是否标注了 RpcService 注解
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
            // 获取 RpcService 注解
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
            // 构建 RpcServiceConfig 对象，从注解中解析出 version 和 group
            RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                    .group(rpcService.group())
                    .version(rpcService.version())
                    .service(bean).build();
            // 服务注册，发生在 Bean 初始化之前
            serviceProvider.publishService(rpcServiceConfig);
        }
        return bean;
    }

    /**
     * 初始化后执行的后处理器
     *
     * @param bean
     * @param beanName
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 获取 Bean 的 Class
        Class<?> targetClass = bean.getClass();
        // 获取 Bean 的 成员属性
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            // 处理标注了 RpcReference 的对象
            RpcReference rpcReference = declaredField.getAnnotation(RpcReference.class);

            if (rpcReference != null) {
                // 构建 RpcServiceConfig 对象，从注解中解析出 version 和 group
                RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                        .group(rpcReference.group())
                        .version(rpcReference.version()).build();
                // 创建当前对象的代理对象
                RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceConfig);
                Object clientProxy = rpcClientProxy.getProxy(declaredField.getType());
                declaredField.setAccessible(true);
                try {
                    // 将代理对象注入
                    declaredField.set(bean, clientProxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }
        return bean;
    }
}
