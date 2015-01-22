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
import com.alibaba.dubbo.common.utils.ExecutorUtil;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.Server;
import com.alibaba.dubbo.remoting.transport.AbstractServer;
import com.alibaba.dubbo.remoting.transport.dispather.ChannelHandlers;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;


/**
 * NettyServer
 *
 * @author Davis Zhao
 */
public class NettyServer extends AbstractServer implements Server {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);


    private Map<String, Channel> channels; // <ip:port, channel>

    private ServerBootstrap bootstrap;

    private io.netty.channel.Channel channel;

    public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, ChannelHandlers.wrap(handler, ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME)));
    }

    @Override
    protected void doOpen() throws Throwable {
        final NettyHandler handler = new NettyHandler(getUrl(), this);
        channels = handler.getChanels();

        bootstrap = new ServerBootstrap();
        NioEventLoopGroup boss = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());
        NioEventLoopGroup worker = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);

        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_BACKLOG, 128);

        bootstrap.group(boss, worker).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
                pipeline.addLast("decoder", adapter.getDecoder());
                pipeline.addLast("encoder", adapter.getEncoder());
                pipeline.addLast("writeHandler", new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        super.write(ctx, msg, promise);
                        NettyChannel nettyChannel = NettyChannel.getOrAddChannel(ctx.channel(), getUrl(), NettyServer.this);
                        try {
//                            NettyServer.this.send(nettyChannel, msg);
                            nettyChannel.send(msg);
                        } finally {
                            NettyChannel.removeChannelIfDisconnected(ctx.channel());
                        }

                    }
                });
                pipeline.addLast("handler", handler);
            }
        });

        channel = bootstrap.bind(getBindAddress()).sync().channel();
    }


    @Override
    protected void doClose() throws Throwable {
        logger.debug("---Server closed---");

//        try {
//            if (channel != null) {
//                // unbind.
//                channel.close();
//            }
//        } catch (Throwable e) {
//            logger.warn(e.getMessage(), e);
//        }
//        try {
//            Collection<Channel> channels = getChannels();
//            if (channels != null && channels.size() > 0) {
//                for (Channel channel : channels) {
//                    try {
//                        channel.close();
//                    } catch (Throwable e) {
//                        logger.warn(e.getMessage(), e);
//                    }
//                }
//            }
//        } catch (Throwable e) {
//            logger.warn(e.getMessage(), e);
//        }
//
//        try {
//            if (channels != null) {
//                channels.clear();
//            }
//        } catch (Throwable e) {
//            logger.warn(e.getMessage(), e);
//        }
        EventLoopGroup workerGroup = bootstrap.childGroup();
        EventLoopGroup bossGroup = bootstrap.group();

        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();

        bossGroup.terminationFuture().sync();
        workerGroup.terminationFuture().sync();
        logger.debug("---Server really closed---");
    }

    public Collection<Channel> getChannels() {
        Collection<Channel> chs = new HashSet<>();
        for (Channel channel : this.channels.values()) {
            if (channel.isConnected()) {
                chs.add(channel);
            } else {
                channels.remove(NetUtils.toAddressString(channel.getRemoteAddress()));
            }
        }
        return chs;
    }

    public Channel getChannel(InetSocketAddress remoteAddress) {
        return channels.get(NetUtils.toAddressString(remoteAddress));
    }

    public boolean isBound() {
        return channel.isActive();
    }

}