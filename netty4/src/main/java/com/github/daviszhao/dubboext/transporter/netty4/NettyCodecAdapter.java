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

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.io.Bytes;
import com.alibaba.dubbo.common.io.UnsafeByteArrayInputStream;
import com.alibaba.dubbo.common.io.UnsafeByteArrayOutputStream;
import com.alibaba.dubbo.remoting.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.io.IOException;
import java.util.List;

/**
 * NettyCodecAdapter.
 *
 * @author Davis Zhao
 */
final class NettyCodecAdapter {

    private final ChannelHandler encoder = new InternalEncoder();

    private final ChannelHandler decoder = new InternalDecoder();

    private final Codec codec;

    private final URL url;

    private final int bufferSize;

    private final com.alibaba.dubbo.remoting.ChannelHandler handler;

    public NettyCodecAdapter(Codec codec, URL url, com.alibaba.dubbo.remoting.ChannelHandler handler) {
        this.codec = codec;
        this.url = url;
        this.handler = handler;
        int b = url.getPositiveParameter(Constants.BUFFER_KEY, Constants.DEFAULT_BUFFER_SIZE);
        this.bufferSize = b >= Constants.MIN_BUFFER_SIZE && b <= Constants.MAX_BUFFER_SIZE ? b : Constants.DEFAULT_BUFFER_SIZE;
    }

    public ChannelHandler getEncoder() {
        return encoder;
    }

    public ChannelHandler getDecoder() {
        return decoder;
    }

    @Sharable
    private class InternalEncoder extends MessageToMessageEncoder<Object> {

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            UnsafeByteArrayOutputStream os = new UnsafeByteArrayOutputStream(1024);
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
            try {
                codec.encode(channel, os, msg);
            } finally {
                NettyChannel.removeChannelIfDisconnected(ctx.channel());
            }
        }
    }

    private class InternalDecoder extends ByteToMessageDecoder {

        private int mOffset = 0, mLimit = 0;

        private byte[] mBuffer = null;


        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            int readable = in.readableBytes();
            if (readable <= 0) return;
            int off = 0, limit = 0;
            byte[] buff = mBuffer;
            if (buff == null) {
                buff = new byte[bufferSize];
                off = limit = 0;
            }
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
            boolean remaining = true;
            Object msg;
            UnsafeByteArrayInputStream is;
            try {
                do {
                    int read = Math.min(readable, buff.length - limit);
                    in.readBytes(buff, limit, read);
                    limit += read;
                    readable -= read;
                    is = new UnsafeByteArrayInputStream(buff, off, limit - off);
                    do {
                        try {
                            msg = codec.decode(channel, is);
                        } catch (IOException e) {
                            remaining = false;
                            throw e;
                        }
                        if (msg == Codec.NEED_MORE_INPUT) {
                            if (off == 0) {
                                if (readable > 0) {
                                    buff = Bytes.copyOf(buff, buff.length << 1);
                                }
                            } else {
                                int len = limit - off;
                                System.arraycopy(buff, off, buff, 0, len);
                                off = 0;
                                limit = len;
                            }
                            break;
                        } else {
                            int pos = is.position();
                            if (off == pos) {
                                remaining = false;
                                throw new IOException("Decode without read data.");
                            }
                            if (msg != null) {
                                out.add(msg);
                            }
                            off = pos;
                        }

                    } while (is.available() > 0);
                } while (readable > 0);
            } finally {
                if (remaining) {
                    int len = limit - off;
                    if (len < buff.length / 2) {
                        System.arraycopy(buff, off, buff, 0, len);
                        off = 0;
                        limit = len;
                    }
                    mBuffer = buff;
                    mOffset = off;
                    mLimit = limit;
                } else {
                    mBuffer = null;
                    mOffset = mLimit = 0;
                }
            }
            NettyChannel.removeChannelIfDisconnected(ctx.channel());
        }
    }
}