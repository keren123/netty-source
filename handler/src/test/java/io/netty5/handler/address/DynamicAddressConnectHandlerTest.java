/*
 * Copyright 2019 The Netty Project
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
package io.netty5.handler.address;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DynamicAddressConnectHandlerTest {
    private static final SocketAddress LOCAL = new SocketAddress() { };
    private static final SocketAddress LOCAL_NEW = new SocketAddress() { };
    private static final SocketAddress REMOTE = new SocketAddress() { };
    private static final SocketAddress REMOTE_NEW = new SocketAddress() { };
    @Test
    public void testReplaceAddresses() throws Exception {

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public Future<Void> connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                        SocketAddress localAddress) {
                try {
                    assertSame(REMOTE_NEW, remoteAddress);
                    assertSame(LOCAL_NEW, localAddress);
                    return ctx.newSucceededFuture();
                } catch (Throwable cause) {
                    return ctx.newFailedFuture(cause);
                }
            }
        }, new DynamicAddressConnectHandler() {
            @Override
            protected SocketAddress localAddress(SocketAddress remoteAddress, SocketAddress localAddress) {
                assertSame(REMOTE, remoteAddress);
                assertSame(LOCAL, localAddress);
                return LOCAL_NEW;
            }

            @Override
            protected SocketAddress remoteAddress(SocketAddress remoteAddress, SocketAddress localAddress) {
                assertSame(REMOTE, remoteAddress);
                assertSame(LOCAL, localAddress);
                return REMOTE_NEW;
            }
        });
        channel.connect(REMOTE, LOCAL).asStage().sync();
        assertNull(channel.pipeline().get(DynamicAddressConnectHandler.class));
        assertFalse(channel.finish());
    }

    @Test
    public void testLocalAddressThrows() {
        testThrows0(true);
    }

    @Test
    public void testRemoteAddressThrows() {
        testThrows0(false);
    }

    private static void testThrows0(final boolean localThrows) {
        final IllegalStateException exception = new IllegalStateException();

        EmbeddedChannel channel = new EmbeddedChannel(new DynamicAddressConnectHandler() {
            @Override
            protected SocketAddress localAddress(
                    SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
                if (localThrows) {
                    throw exception;
                }
                return super.localAddress(remoteAddress, localAddress);
            }

            @Override
            protected SocketAddress remoteAddress(SocketAddress remoteAddress, SocketAddress localAddress)
                    throws Exception {
                if (!localThrows) {
                    throw exception;
                }
                return super.remoteAddress(remoteAddress, localAddress);
            }
        });
        assertSame(exception, channel.connect(REMOTE, LOCAL).cause());
        assertNotNull(channel.pipeline().get(DynamicAddressConnectHandler.class));
        assertFalse(channel.finish());
    }
}
