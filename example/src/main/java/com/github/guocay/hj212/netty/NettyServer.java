package com.github.guocay.hj212.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
public class NettyServer implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private Channel channel;

    @Autowired
    private NettyServerHandler serverHandler;

    public ChannelFuture start(String hostname,int port) throws Exception {
        ChannelFuture f = null;
        try {
            //ServerBootstrap负责初始化netty服务器，并且开始监听端口的socket请求
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(hostname,port))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            // 先按回车换行符拆帧，解决TCP粘包/拆包问题
                            // HJ212通信包结尾固定为 \r\n，LineBasedFrameDecoder会自动按行分割
                            // maxLength = 2048，覆盖数据段最大1024 + 包头/长度/CRC/包尾等额外字符
                            socketChannel.pipeline().addLast(new LineBasedFrameDecoder(2048));
                            socketChannel.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
                            socketChannel.pipeline().addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));
//                            为监听客户端read/write事件的Channel添加用户自定义的ChannelHandler
                            socketChannel.pipeline().addLast(serverHandler);
                        }
                    });

            f = b.bind().sync();
            channel = f.channel();
            log.info("Netty TcpServer Start Success...");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (f != null && f.isSuccess()) {
                log.info("Netty server listening " + hostname + " on port " + port + " and ready for connections...");
            } else {
                log.error("Netty server start up Error!");
            }
        }
        return f;
    }

    @Override
    public void destroy() {
        log.info("Shutdown Netty Server...");
        if(channel != null) { channel.close();}
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        log.info("Shutdown Netty Server Success!");
    }

}
