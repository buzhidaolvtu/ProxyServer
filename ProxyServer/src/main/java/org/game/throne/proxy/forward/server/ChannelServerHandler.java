package org.game.throne.proxy.forward.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCounted;
import org.game.throne.proxy.forward.ChannelRelationEvent;
import org.game.throne.proxy.forward.client.LocalHandler;
import org.game.throne.proxy.forward.relation.RelationKeeper;
import org.game.throne.proxy.forward.relation.RelationProcess;
import org.game.throne.proxy.forward.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Created by lvtu on 2017/9/6.
 */
@ChannelHandler.Sharable
public class ChannelServerHandler extends SimpleChannelInboundHandler implements RelationProcess {

    private final static Logger logger = LoggerFactory.getLogger(LocalHandler.class);

    protected ServerHandler serverHandler = null;

    private BlockingDeque<ChannelHandlerContext> contextObjectPool = new LinkedBlockingDeque();

    private RelationKeeper relationKeeper;

    public ChannelServerHandler(RelationKeeper relationKeeper) {
        this.relationKeeper = relationKeeper;
    }

    public ChannelHandlerContext getAvailableContext() {
        try {
            return contextObjectPool.poll(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ChannelHandlerContext serverConext(ChannelHandlerContext ctx) {
        return relationKeeper.matchedContext(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("connected, handler:{},channel:{}", ctx, ctx.channel());
        //保存这个ChannelHandlerContext
        contextObjectPool.offer(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        writeToNextChannel(ctx, msg);
        if (msg instanceof LastHttpContent) {
            flushToNextChannel(ctx);
            responseFinishedNotify(ctx);
        }
    }

    private void writeToNextChannel(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ReferenceCounted) {
            logger.debug("Retaining reference counted message");
            ((ReferenceCounted) msg).retain();
        }
        logger.info("start to get next context.");
        ChannelHandlerContext serverContext = serverConext(ctx);
        logger.info("data arrived. from channel:{},start to write into next channel:{}, msg:{}", ctx.channel(), serverContext.channel(), msg);
        serverContext.write(msg).addListener(FutureUtil.errorLogListener(ctx));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        requestBreakRelation(ctx);
        cause.printStackTrace();
    }

    private void flushToNextChannel(ChannelHandlerContext ctx) {
        ChannelHandlerContext clientConext = serverConext(ctx);
        logger.info("flush data. from channel:{},start to flush into next channel:{}", ctx.channel(), clientConext.channel());
        clientConext.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        contextObjectPool.remove(ctx);
        requestBreakRelation(ctx);
        super.channelInactive(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (ChannelInputShutdownEvent.INSTANCE.equals(evt)) {
            // TODO: 2017/9/7
            logger.error("CHANNEL CLIENT BREAK connection.");
            return;
        }
        if (ChannelRelationEvent.BREAK.equals(evt)) {
            logger.debug("BREAK EVENT received.");
            responseBreakRelation(ctx);
        }
    }

    private void responseFinishedNotify(ChannelHandlerContext ctx) {
        serverConext(ctx).pipeline().fireUserEventTriggered(ChannelRelationEvent.RESPONSE_FINISHED);
    }

    @Override
    public void requestBreakRelation(ChannelHandlerContext ctx) {
        relationKeeper.breakRelation(ctx);
    }

    @Override
    public void responseBreakRelation(ChannelHandlerContext ctx) {
        try {
            contextObjectPool.offer(ctx);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
