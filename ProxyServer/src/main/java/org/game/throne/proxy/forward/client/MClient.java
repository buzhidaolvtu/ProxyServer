package org.game.throne.proxy.forward.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.game.throne.proxy.forward.ChannelHandlerFactory;

/**
 * Created by lvtu on 2017/9/1.
 */
public class MClient {

    private String host;
    private int port;
    private ChannelHandler[] handler;

    public MClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public MClient withHandler(ChannelHandler... handler) {
        this.handler = handler;
        return this;
    }

    private ChannelHandlerFactory[] factories;

    public MClient withHandlerFactory(ChannelHandlerFactory... factories) {
        this.factories = factories;
        return this;
    }

    public MClient connect() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap(); // (1)
            b.group(workerGroup); // (2)
            b.channel(NioSocketChannel.class); // (3)
            b.option(ChannelOption.SO_KEEPALIVE, true); // (4)
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    if (factories != null && factories.length > 0) {
                        for (int i = 0; i < factories.length; i++) {
                            pipeline.addLast(factories[i].create());
                        }
                    }
                    pipeline.addLast(handler);
                }
            });

            // Start the client.
            ChannelFuture f = b.connect(host, port).sync(); // (5)

            //TODO 释放资源被注释掉了
            // Wait until the connection is closed.
//            f.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
//            workerGroup.shutdownGracefully();
        }
        return this;
    }
}