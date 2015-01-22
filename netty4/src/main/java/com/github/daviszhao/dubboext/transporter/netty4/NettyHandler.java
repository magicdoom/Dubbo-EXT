/*
 * Copyright 2014-2015 Davis Zhao.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.daviszhao.dubboext.transporter.netty4;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//import io.netty.channel.ChannelStateEvent;
//import io.netty.channel.ExceptionEvent;
//import io.netty.channel.MessageEvent;
//import io.netty.channel.SimpleChannelHandler;

/**
 * NettyHandler
 *
 * @author Davis Zhao
 */
@Sharable
public class NettyHandler extends ChannelInboundHandlerAdapter {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final URL url;

    private final ChannelHandler handler;

    public NettyHandler(URL url, ChannelHandler handler) {
        assert url != null : "url can not be NULL";
        assert handler != null : "handler can not be NULL";
        this.url = url;
        this.handler = handler;
    }

    public Map<String, Channel> getChanels() {
        return channels;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NettyChannel nettyChannel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        try {
            if (nettyChannel != null) {
                channels.put(NetUtils.toAddressString((InetSocketAddress) ctx.channel().remoteAddress()), nettyChannel);
            }
            handler.connected(nettyChannel);
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.channel());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyChannel nettyChannel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        try {
            handler.received(nettyChannel, msg);
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        NettyChannel nettyChannel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        try {
            handler.caught(nettyChannel, cause);
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.channel());
        }
    }
}