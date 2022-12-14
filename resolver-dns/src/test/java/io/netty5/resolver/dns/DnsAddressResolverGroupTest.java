/*
 * Copyright 2020 The Netty Project
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
package io.netty5.resolver.dns;

import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.resolver.AddressResolver;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsAddressResolverGroupTest {
    @Test
    public void testUseConfiguredEventLoop() throws InterruptedException {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        final EventLoop loop = group.next();
        MultithreadEventLoopGroup defaultEventLoopGroup = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder()
                .eventLoop(loop).channelType(NioDatagramChannel.class);
        DnsAddressResolverGroup resolverGroup = new DnsAddressResolverGroup(builder);
        try {
            final Promise<?> promise = loop.newPromise();
            AddressResolver<?> resolver = resolverGroup.getResolver(defaultEventLoopGroup.next());
            resolver.resolve(new SocketAddress() {
                private static final long serialVersionUID = 3169703458729818468L;
            }).addListener(future -> {
                try {
                    assertThat(future.cause(),
                               instanceOf(UnsupportedAddressTypeException.class));
                    assertTrue(loop.inEventLoop());
                    promise.setSuccess(null);
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                }
            }).asStage().await();
            promise.asFuture().asStage().sync();
        } finally {
            resolverGroup.close();
            group.shutdownGracefully();
            defaultEventLoopGroup.shutdownGracefully();
        }
    }
}
