package com.github.daviszhao.dubboext.transporter.netty4;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Created by daviszhao on 2015/1/23.
 */
public class NoNetty3Test {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void Test() {

        expectedException.expect(NoClassDefFoundError.class);
        expectedException.expectMessage("org/jboss/netty/channel/ChannelFactory");
        ApplicationConfig applicationConfig = new ApplicationConfig("test_no_netty3");
        RegistryConfig registryConfig = new RegistryConfig("multicast://224.5.2.3:9872");
        ProtocolConfig protocolConfig = new ProtocolConfig("dubbo", 3212);
        //Default transporter is "netty",means netty3,now it should throw a Exception
        ServiceConfig<DemoService> serviceConfig = new ServiceConfig<>();
        serviceConfig.setApplication(applicationConfig);
        serviceConfig.setRegistry(registryConfig);
        serviceConfig.setProtocol(protocolConfig);
        serviceConfig.setInterface(DemoService.class);
        serviceConfig.setRef(new DemoServiceImpl());
        serviceConfig.export();
    }
}
