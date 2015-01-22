package com.github.daviszhao.dubboext.transporter.netty4;

/**
 * Created by daviszhao on 2015/1/22.
 */
public class DemoServiceImpl implements DemoService {
    @Override
    public String hello() {
        return "Hello world";
    }
}
