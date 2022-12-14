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
package io.netty5.example.securechat;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.group.ChannelGroup;
import io.netty5.channel.group.DefaultChannelGroup;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.util.concurrent.GlobalEventExecutor;

import java.net.InetAddress;

/**
 * Handles a server-side channel.
 */
public class SecureChatServerHandler extends SimpleChannelInboundHandler<String> {

    static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent completionEvent = (SslHandshakeCompletionEvent) evt;
            // Once session is secured, send a greeting and register the channel to the global channel
            // list so the channel received the messages from others.
            if (completionEvent.isSuccess()) {
                ctx.writeAndFlush(
                        "Welcome to " + InetAddress.getLocalHost().getHostName() + " secure chat service!\n");
                ctx.writeAndFlush(
                        "Your session is protected by " +
                                completionEvent.session().getCipherSuite() +
                                " cipher suite.\n");
                channels.add(ctx.channel());
            } else {
                ctx.close();
            }
        }
        super.channelInboundEvent(ctx, evt);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
        // Send the received message to all channels but the current one.
        for (Channel c: channels) {
            if (c != ctx.channel()) {
                c.writeAndFlush("[" + ctx.channel().remoteAddress() + "] " + msg + '\n');
            } else {
                c.writeAndFlush("[you] " + msg + '\n');
            }
        }

        // Close the connection if the client has sent 'bye'.
        if ("bye".equals(msg.toLowerCase())) {
            ctx.close();
        }
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
