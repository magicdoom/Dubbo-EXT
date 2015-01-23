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
import com.alibaba.dubbo.remoting.Codec2;
import com.alibaba.dubbo.remoting.buffer.ChannelBuffer;
import com.alibaba.dubbo.remoting.buffer.ChannelBuffers;
import com.alibaba.dubbo.remoting.buffer.DynamicChannelBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
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

    private final Codec2 codec;

    private final URL url;

    private final int bufferSize;

    private final com.alibaba.dubbo.remoting.ChannelHandler handler;

    public NettyCodecAdapter(Codec2 codec, URL url, com.alibaba.dubbo.remoting.ChannelHandler handler) {
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
            ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(1024);
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
            try {
                codec.encode(channel, buffer, msg);
            } finally {
                NettyChannel.removeChannelIfDisconnected(ctx.channel());
            }
            out.add(Unpooled.wrappedBuffer(buffer.toByteBuffer()));
        }
    }

    private class InternalDecoder extends SimpleChannelInboundHandler<ByteBuf> {
        private ChannelBuffer buffer = ChannelBuffers.EMPTY_BUFFER;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            int readable = msg.readableBytes();
            if (readable <= 0) return;

            ChannelBuffer cb;
            if (buffer.readable()) {
                if (buffer instanceof DynamicChannelBuffer) {
                    buffer.writeBytes(msg.nioBuffer());
                    cb = buffer;
                } else {
                    int size = buffer.readableBytes() + msg.readableBytes();
                    cb = ChannelBuffers.dynamicBuffer(size > bufferSize ? size : bufferSize);
                    cb.writeBytes(buffer, buffer.readableBytes());
                    cb.writeBytes(msg.nioBuffer());
                }
            } else {
                cb = ChannelBuffers.wrappedBuffer(msg.nioBuffer());
            }

            NettyChannel nettyChannel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
            Object o;
            int saveReaderIndex;
            try {
                do {
                    saveReaderIndex = cb.readerIndex();
                    try {
                        o = codec.decode(nettyChannel, cb);
                    } catch (IOException e) {
                        buffer = ChannelBuffers.EMPTY_BUFFER;
                        throw e;
                    }

                    if (o == Codec2.DecodeResult.NEED_MORE_INPUT) {
                        cb.readerIndex(saveReaderIndex);
                        break;
                    } else {
                        if (saveReaderIndex == cb.readerIndex()) {
                            buffer = ChannelBuffers.EMPTY_BUFFER;
                            throw new IOException("Decode without read data");
                        }

                        if (o != null) {
                            ctx.fireChannelRead(o);
                        }
                    }
                } while (cb.readable());
            } finally {
                if (cb.readable()) {
                    cb.discardReadBytes();
                    buffer = cb;
                } else {
                    buffer = ChannelBuffers.EMPTY_BUFFER;
                }

                NettyChannel.removeChannelIfDisconnected(ctx.channel());
            }
        }
    }
/*    private class InternalDecoder extends ByteToMessageDecoder {


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
            ChannelBuffer channelBuffer;
            try {
                do {
                    int read = Math.min(readable, buff.length - limit);
                    in.readBytes(buff, limit, read);
                    limit += read;
                    readable -= read;
                    channelBuffer = ChannelBuffers.dynamicBuffer(1024);
                    do {
                        try {
                            msg = codec.decode(channel, channelBuffer);
                        } catch (IOException e) {
                            remaining = false;
                            throw e;
                        }
                        if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
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
                            int pos = channelBuffer.position();
                            if (off == pos) {
                                remaining = false;
                                throw new IOException("Decode without read data.");
                            }
                            if (msg != null) {
                                out.add(msg);
                            }
                            off = pos;
                        }

                    } while (channelBuffer.available() > 0);
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

    }*/
}