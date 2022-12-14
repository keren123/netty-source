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
package io.netty5.handler.ssl;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.netty5.handler.ssl.CloseNotifyTest.assertCloseNotify;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ApplicationProtocolNegotiationHandlerTest {

    @Test
    public void testRemoveItselfIfNoSslHandshakeCompletionEvent() throws NoSuchAlgorithmException {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // This test is mocked/simulated and doesn't go through full TLS handshake. Currently only JDK SSLEngineImpl
        // client mode will generate a close_notify.
        engine.setUseClientMode(true);

        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        String msg = "msg";
        String msg2 = "msg2";

        assertTrue(channel.writeInbound(msg));
        assertTrue(channel.writeInbound(msg2));
        assertNull(channel.pipeline().context(alpnHandler));
        assertEquals(msg, channel.readInbound());
        assertEquals(msg2, channel.readInbound());

        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testHandshakeFailure() {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        SSLHandshakeException exception = new SSLHandshakeException("error");
        SslHandshakeCompletionEvent completionEvent = new SslHandshakeCompletionEvent(exception);
        channel.pipeline().fireChannelInboundEvent(completionEvent);
        channel.pipeline().fireChannelExceptionCaught(new DecoderException(exception));
        assertNull(channel.pipeline().context(alpnHandler));
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testHandshakeSuccess() throws NoSuchAlgorithmException {
        testHandshakeSuccess0(false);
    }

    @Test
    public void testHandshakeSuccessWithSslHandlerAddedLater() throws NoSuchAlgorithmException {
        testHandshakeSuccess0(true);
    }

    private static void testHandshakeSuccess0(boolean addLater) throws NoSuchAlgorithmException {
        final AtomicBoolean configureCalled = new AtomicBoolean(false);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                configureCalled.set(true);
                assertEquals(ApplicationProtocolNames.HTTP_1_1, protocol);
            }
        };

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // This test is mocked/simulated and doesn't go through full TLS handshake. Currently only JDK SSLEngineImpl
        // client mode will generate a close_notify.
        engine.setUseClientMode(true);

        EmbeddedChannel channel = new EmbeddedChannel();
        if (addLater) {
            channel.pipeline().addLast(alpnHandler);
            channel.pipeline().addFirst(new SslHandler(engine));
        } else {
            channel.pipeline().addLast(new SslHandler(engine));
            channel.pipeline().addLast(alpnHandler);
        }
        channel.pipeline().fireChannelInboundEvent(
                new SslHandshakeCompletionEvent(engine.getSession(), ApplicationProtocolNames.HTTP_1_1));
        assertNull(channel.pipeline().context(alpnHandler));
        // Should produce the close_notify messages
        channel.releaseOutbound();
        channel.close();
        assertCloseNotify(channel.readOutbound());
        channel.finishAndReleaseAll();
        assertTrue(configureCalled.get());
    }

    @Test
    public void testHandshakeSuccessButNoSslHandler() {
        final AtomicBoolean configureCalled = new AtomicBoolean(false);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                configureCalled.set(true);
                assertEquals(ApplicationProtocolNames.HTTP_1_1, protocol);
            }
        };
        final EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        channel.pipeline().fireChannelInboundEvent(
                new SslHandshakeCompletionEvent(new TestSSLSession(), ApplicationProtocolNames.HTTP_1_1));
        assertNull(channel.pipeline().context(alpnHandler));
        channel.finishAndReleaseAll();
        assertTrue(configureCalled.get());
    }

    @Test
    public void testBufferMessagesUntilHandshakeComplete() throws Exception {
        testBufferMessagesUntilHandshakeComplete(null);
    }

    @Test
    public void testBufferMessagesUntilHandshakeCompleteWithClose() throws Exception {
        testBufferMessagesUntilHandshakeComplete(ctx -> ctx.channel().close());
    }

    @Test
    public void testBufferMessagesUntilHandshakeCompleteWithInputShutdown() throws Exception {
        testBufferMessagesUntilHandshakeComplete(ctx -> ctx.fireChannelShutdown(ChannelShutdownDirection.Inbound));
    }

    private static void testBufferMessagesUntilHandshakeComplete(
            final Consumer<ChannelHandlerContext> pipelineConfigurator)
            throws Exception {
        final AtomicReference<byte[]> channelReadData = new AtomicReference<byte[]>();
        final AtomicBoolean channelReadCompleteCalled = new AtomicBoolean(false);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                assertEquals(ApplicationProtocolNames.HTTP_1_1, protocol);
                ctx.pipeline().addLast(new ChannelHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        channelReadData.set((byte[]) msg);
                    }

                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) {
                        channelReadCompleteCalled.set(true);
                    }
                });
                if (pipelineConfigurator != null) {
                    pipelineConfigurator.accept(ctx);
                }
            }
        };

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // This test is mocked/simulated and doesn't go through full TLS handshake. Currently only JDK SSLEngineImpl
        // client mode will generate a close_notify.
        engine.setUseClientMode(true);

        final byte[] someBytes = new byte[1024];

        EmbeddedChannel channel = new EmbeddedChannel(new SslHandler(engine), new ChannelHandler() {
            @Override
            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof SslHandshakeCompletionEvent && ((SslHandshakeCompletionEvent) evt).isSuccess()) {
                    ctx.fireChannelRead(someBytes);
                }
                ctx.fireChannelInboundEvent(evt);
            }
        }, alpnHandler);
        channel.pipeline().fireChannelInboundEvent(new SslHandshakeCompletionEvent(engine.getSession(), null));
        assertNull(channel.pipeline().context(alpnHandler));
        assertArrayEquals(someBytes, channelReadData.get());
        assertTrue(channelReadCompleteCalled.get());
        assertNull(channel.readInbound());
        assertTrue(channel.finishAndReleaseAll());
    }

    private static final class TestSSLSession implements SSLSession {
        @Override
        public byte[] getId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLSessionContext getSessionContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getCreationTime() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLastAccessedTime() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValid() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putValue(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getValue(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeValue(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getValueNames() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Certificate[] getLocalCertificates() {
            throw new UnsupportedOperationException();
        }

        @Override
        public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Principal getLocalPrincipal() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCipherSuite() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getProtocol() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPeerHost() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPeerPort() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPacketBufferSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getApplicationBufferSize() {
            throw new UnsupportedOperationException();
        }
    }
}
