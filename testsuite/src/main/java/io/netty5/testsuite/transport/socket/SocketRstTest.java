/*
 * Copyright 2016 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketRstTest extends AbstractSocketTest {
    protected void assertRstOnCloseException(IOException cause, Channel clientChannel) {
        if (Locale.getDefault() == Locale.US || Locale.getDefault() == Locale.UK) {
            assertTrue(cause.getMessage().contains("reset") || cause.getMessage().contains("closed"),
                "actual message: " + cause.getMessage());
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testSoLingerZeroCausesOnlyRstOnClose(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSoLingerZeroCausesOnlyRstOnClose);
    }

    public void testSoLingerZeroCausesOnlyRstOnClose(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<>();
        final AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        // SO_LINGER=0 means that we must send ONLY a RST when closing (not a FIN + RST).
        sb.childOption(ChannelOption.SO_LINGER, 0);
        sb.childHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverChannelRef.compareAndSet(null, ch);
                latch.countDown();
            }
        });
        cb.handler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new ChannelHandler() {
                    @Override
                    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        throwableRef.compareAndSet(null, cause);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        latch2.countDown();
                    }
                });
            }
        });
        Channel sc = sb.bind().asStage().get();
        Channel cc = cb.connect(sc.localAddress()).asStage().get();

        // Wait for the server to get setup.
        latch.await();

        // The server has SO_LINGER=0 and so it must send a RST when close is called.
        serverChannelRef.get().close();

        // Wait for the client to get channelInactive.
        latch2.await();

        // Verify the client received a RST.
        Throwable cause = throwableRef.get();
        assertTrue(cause instanceof IOException,
                   "actual [type, message]: [" + cause.getClass() + ", " + cause.getMessage() + ']');

        assertRstOnCloseException((IOException) cause, cc);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testNoRstIfSoLingerOnClose(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testNoRstIfSoLingerOnClose);
    }

    public void testNoRstIfSoLingerOnClose(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final AtomicReference<Channel> serverChannelRef = new AtomicReference<>();
        final AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        sb.childHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverChannelRef.compareAndSet(null, ch);
                latch.countDown();
            }
        });
        cb.handler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new ChannelHandler() {
                    @Override
                    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        throwableRef.compareAndSet(null, cause);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        latch2.countDown();
                    }
                });
            }
        });
        Channel sc = sb.bind().asStage().get();
        cb.connect(sc.localAddress()).asStage().sync();

        // Wait for the server to get setup.
        latch.await();

        // The server has SO_LINGER=0 and so it must send a RST when close is called.
        serverChannelRef.get().close();

        // Wait for the client to get channelInactive.
        latch2.await();

        // Verify the client did not received a RST.
        assertNull(throwableRef.get());
    }
}
