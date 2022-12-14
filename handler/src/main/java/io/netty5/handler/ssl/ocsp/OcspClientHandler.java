/*
 * Copyright 2017 The Netty Project
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
package io.netty5.handler.ssl.ocsp;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty5.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.util.internal.UnstableApi;

import javax.net.ssl.SSLHandshakeException;

import static java.util.Objects.requireNonNull;

/**
 * A handler for SSL clients to handle and act upon stapled OCSP responses.
 *
 * @see ReferenceCountedOpenSslContext#enableOcsp()
 * @see ReferenceCountedOpenSslEngine#getOcspResponse()
 */
@UnstableApi
public abstract class OcspClientHandler implements ChannelHandler {

    private final ReferenceCountedOpenSslEngine engine;

    protected OcspClientHandler(ReferenceCountedOpenSslEngine engine) {
        this.engine = requireNonNull(engine, "engine");
    }

    /**
     * @see ReferenceCountedOpenSslEngine#getOcspResponse()
     */
    protected abstract boolean verify(ChannelHandlerContext ctx, ReferenceCountedOpenSslEngine engine) throws Exception;

    @Override
    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireChannelInboundEvent(evt);
        if (evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
            if (event.isSuccess() && !verify(ctx, engine)) {
                throw new SSLHandshakeException("Bad OCSP response");
            }
            ctx.pipeline().remove(this);
        }
    }
}
