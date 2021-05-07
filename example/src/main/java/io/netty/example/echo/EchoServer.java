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
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure the server.
        // 主从Reactor多线程模式
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // boss线程组，主要用于服务端接收客户端连接(OP_CONNECT)
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // worker线程组，主要用于进行SocketChannel 的网络读(OP_READ)、写（OP_WRITE）
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup) // 设置要使用的EventLoopGroup
                    .channel(NioServerSocketChannel.class) // 设置Channel类型，服务端是NioServerSocketChannel（执行bind时会用到，详见注释）
                    .option(ChannelOption.SO_BACKLOG, 100) // 确定Channel类型之后，设置Channel参数(作为服务端主要设置tcp的backing参数)
                    .handler(new LoggingHandler(LogLevel.INFO)) // 为NioServerSocketChannel添加 ChannelHandler （所有连接该监听端口的客户端都会执行它，父类 AbstractBootstrap 中的 Handler 是一个工厂类，它为每一个新接入的客户端都创建一个新的 Handler？？）
                    // 两种设置keepalive风格
//                    .childOption(NioChannelOption.SO_KEEPALIVE, true)
//                    .childOption(ChannelOption.SO_KEEPALIVE, true)

                    // 切换Pooled方式之一(池化与非池化); 默认是池化； 也可以通过配置来切换ByteBufUtil#DEFAULT_ALLOCATOR
                    .childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT)

                    // ChannelInitializer（相当于一个中介，媒婆） 一次性，初始化handler（负责添加一个handler后，自己就移除了）
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 设置并添加ChannelHandler
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc()));
                            }
                            // p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(serverHandler);
                        }
                    });

            // Start the server.
            ChannelFuture f = b.bind(PORT) // 绑定 ServerChannel
                    .sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            // 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
