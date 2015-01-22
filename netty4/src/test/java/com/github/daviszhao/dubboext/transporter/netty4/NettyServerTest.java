package com.github.daviszhao.dubboext.transporter.netty4;

import com.alibaba.dubbo.config.ProtocolConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by daviszhao on 2015/1/22.
 */
public class NettyServerTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private ApplicationContext provider;
    private ApplicationContext consumer;

    @Before
    public void setup() {
        provider = new ClassPathXmlApplicationContext("spring-provider.xml");
    }

    @Test
    public void testProvice() {
        ProtocolConfig dubbo = (ProtocolConfig) provider.getBean("dubbo");
        Assert.assertEquals(dubbo.getTransporter(), "netty4");

    }

    @Test
    public void testConsumer() {
        consumer = new ClassPathXmlApplicationContext("spring-consumer.xml");
        DemoService demo = (DemoService) consumer.getBean("demo");
    }


}
