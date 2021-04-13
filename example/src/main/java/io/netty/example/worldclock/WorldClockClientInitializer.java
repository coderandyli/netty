/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.worldclock;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;

public class WorldClockClientInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;

    public WorldClockClientInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc(), WorldClockClient.HOST, WorldClockClient.PORT));
        }

        // Protobuf的使用
        p.addLast(new ProtobufVarint32FrameDecoder()); // 进行【一次解码】，得到ByteBuf （长度是可变的）
        p.addLast(new ProtobufDecoder(WorldClockProtocol.LocalTimes.getDefaultInstance())); // 进行【二次解码】，得到可操作的Java对象

        p.addLast(new ProtobufVarint32LengthFieldPrepender()); // 对端接收到信息后，要进行粘包和半包问题  （长度是可变的）
        p.addLast(new ProtobufEncoder());

        // 业务逻辑处理
        p.addLast(new WorldClockClientHandler());
    }
}
