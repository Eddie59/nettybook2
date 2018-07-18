/*
 * Copyright 2013-2018 Lilinfeng.
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
package com.phei.netty.frame.fault;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author lilinfeng
 * @version 1.0
 * @date 2014年2月14日
 */
public class TimeServer {

    public void bind(int port) throws Exception {
        // 配置服务端的NIO线程组，bossGroup接受来自客户端的连接，workerGroup对网络IO进行读写
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            //ServerBootstrap引导使用Netty构建项目
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChildChannelHandler());//绑定IO事件的处理类，用于处理网络IO事件，比如记日志，对消息编码

            //bind(int)方法将服务端的Channel绑定到端口，绑定成功后将接受客户端的连接
            //sync()方法是由于Netty中的事件都是异步的，所以需要同步等待绑定结果
            ChannelFuture f = b.bind(port).sync();

            //使当前main线程阻塞而不立即执行之后的各种shutdown()方法，
            // 其语义是等到服务端接受客户端连接的Channel被关闭时，才执行后面代码的操作
            f.channel().closeFuture().sync();
        } finally {
            // 优雅退出，释放线程池资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 每个Channel被创建的时候都需要被关联一个对应的pipeline（通道），这种关联关系是永久的（整个程序运行的生命周期中）。
     * ChannelPipeline可以理解成一个消息（或消息事件，ChanelEvent）流转的通道，在这个通道中可以被附上许多用来处理消息的handler，
     * 当消息在这个通道中流转的时候，如果有与这个消息类型相对应的handler，就会触发这个handler去执行相应的动作。
     *
     */
    private class ChildChannelHandler extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel arg0) throws Exception {
            arg0.pipeline().addLast(new TimeServerHandler());
        }

    }

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args != null && args.length > 0) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {
                // 采用默认值
            }
        }
        new TimeServer().bind(port);
    }
}
