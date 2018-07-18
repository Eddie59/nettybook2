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
package com.phei.netty.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Administrator
 * @version 1.0
 * @date 2014年2月16日
 */
public class MultiplexerTimeServer implements Runnable {
    /**
     * 学校，提供nio大环境
     */
    private ServerSocketChannel servChannel;
    /**
     * 教导处，用来找人
     */
    private Selector selector;
    //老师 (ServerSocket)
    //学生 (SocketChannel)
    //员工号/学生号（SelectionKey）

    private volatile boolean stop;

    /**
     * 初始化多路复用器、绑定监听端口
     *
     * @param port
     */
    public MultiplexerTimeServer(int port) {
        try {
            //教导处
            selector = Selector.open();
            //学校开学
            servChannel = ServerSocketChannel.open();
            servChannel.configureBlocking(false);
            //学校的端口号
            servChannel.socket().bind(new InetSocketAddress(port), 1024);
            //学校有了教导处
            servChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("The time server is start in port : " + port);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void stop() {
        this.stop = true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        while (!stop) {
            try {
                //selector选择准备好IO的channel（教导处查看报名的学生）
                selector.select(1000);
                //获取通道，关心事件的集合
                //这里的集合就是老师和学生的编号集合，如果key是学生的，那就是老学生来问问题，如果key是老师的，那就是招生办的老师带着一个新生来注册
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectedKeys.iterator();
                SelectionKey key = null;
                while (it.hasNext()) {
                    key = it.next();
                    it.remove();
                    try {
                        handleInput(key);
                    } catch (Exception e) {
                        if (key != null) {
                            key.cancel();
                            if (key.channel() != null)
                                key.channel().close();
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // 多路复用器关闭后，所有注册在上面的Channel和Pipe等资源都会被自动去注册并关闭，所以不需要重复释放资源
        if (selector != null)
        {
            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleInput(SelectionKey key) throws IOException {
        if (key.isValid()) {
            //key的状态是OP_ACCEPT，有接受学生的权限，那key一定是老师的了，说明有老师带学生来注册
            if ((key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
                //这是招生老师的Key,招生老师去找学校给这个同学注册
                ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
                //找到学校，报名成功，新同学sc
                SocketChannel sc = ssc.accept();
                sc.configureBlocking(false);
                //在教导处注册，以后教导处能直接找到他，给个新学号
                //注册学号成功，分配学生的权限OP_READ只读，意思是等这位同学再来，就是要提问问题了
                SelectionKey newKey = sc.register(selector, SelectionKey.OP_READ);
                System.out.println( "Got connection from "+sc );
            }
            //OP_READ说明老学生有问题要问
            else if((key.readyOps() & SelectionKey.OP_READ)== SelectionKey.OP_READ){
                SocketChannel sc = (SocketChannel)key.channel();
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                //读数据，查看学生的提问
                int readBytes = sc.read(readBuffer);
                if (readBytes > 0) {
                    readBuffer.flip();
                    byte[] bytes = new byte[readBuffer.remaining()];
                    readBuffer.get(bytes);
                    String body = new String(bytes, "UTF-8");
                    System.out.println("The time server receive order : " + body);
                    String currentTime = "这是答案";
                    doWrite(sc, currentTime);
                } else if (readBytes < 0) {
                    // 对端链路关闭
                    key.cancel();
                    sc.close();
                }
            }
        }
    }

    private void doWrite(SocketChannel channel, String response)
            throws IOException {
        if (response != null && response.trim().length() > 0) {
            byte[] bytes = response.getBytes();
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);
            writeBuffer.flip();
            //给channel学生回复问题答案
            channel.write(writeBuffer);
        }
    }
}
