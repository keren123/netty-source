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
package io.netty5.handler.codec;

import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.TypeParameterMatcher;

import java.util.List;

import static io.netty5.util.internal.SilentDispose.autoClosing;

/**
 * A Codec for on-the-fly encoding/decoding of message.
 *
 * This can be thought of as a combination of {@link MessageToMessageDecoder} and {@link MessageToMessageEncoder}.
 *
 * Here is an example of a {@link MessageToMessageCodec} which just decode from {@link Integer} to {@link Long}
 * and encode from {@link Long} to {@link Integer}.
 *
 * <pre>
 *     public class NumberCodec extends
 *             {@link MessageToMessageCodec}&lt;{@link Integer}, {@link Long}&gt; {
 *         {@code @Override}
 *         public {@link Long} decode({@link ChannelHandlerContext} ctx, {@link Integer} msg, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(msg.longValue());
 *         }
 *
 *         {@code @Override}
 *         public {@link Integer} encode({@link ChannelHandlerContext} ctx, {@link Long} msg, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(msg.intValue());
 *         }
 *     }
 * </pre>
 *
 * Be aware that you need to call {@link ReferenceCounted#retain()} on messages that are just passed through if they
 * are of type {@link ReferenceCounted}. This is needed as the {@link MessageToMessageCodec} will call
 * {@link ReferenceCounted#release()} on encoded / decoded messages.
 */
public abstract class MessageToMessageCodec<INBOUND_IN, OUTBOUND_IN> extends ChannelHandlerAdapter {

    private final MessageToMessageEncoder<Object> encoder = new MessageToMessageEncoder<>() {

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return MessageToMessageCodec.this.acceptOutboundMessage(msg);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void encodeAndClose(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            MessageToMessageCodec.this.encodeAndClose(ctx, (OUTBOUND_IN) msg, out);
        }
    };

    private final MessageToMessageDecoder<Object> decoder = new MessageToMessageDecoder<>() {

        @Override
        public boolean acceptInboundMessage(Object msg) throws Exception {
            return MessageToMessageCodec.this.acceptInboundMessage(msg);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void decodeAndClose(ChannelHandlerContext ctx, Object msg) throws Exception {
            MessageToMessageCodec.this.decodeAndClose(ctx, (INBOUND_IN) msg);
        }
    };

    private final TypeParameterMatcher inboundMsgMatcher;
    private final TypeParameterMatcher outboundMsgMatcher;

    /**
     * Create a new instance which will try to detect the types to decode and encode out of the type parameter
     * of the class.
     */
    protected MessageToMessageCodec() {
        inboundMsgMatcher = TypeParameterMatcher.find(this, MessageToMessageCodec.class, "INBOUND_IN");
        outboundMsgMatcher = TypeParameterMatcher.find(this, MessageToMessageCodec.class, "OUTBOUND_IN");
    }

    /**
     * Create a new instance.
     *
     * @param inboundMessageType    The type of messages to decode
     * @param outboundMessageType   The type of messages to encode
     */
    protected MessageToMessageCodec(
            Class<? extends INBOUND_IN> inboundMessageType, Class<? extends OUTBOUND_IN> outboundMessageType) {
        inboundMsgMatcher = TypeParameterMatcher.get(inboundMessageType);
        outboundMsgMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.channelRead(ctx, msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        return encoder.write(ctx, msg);
    }

    /**
     * Returns {@code true} if and only if the specified message can be decoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return inboundMsgMatcher.match(msg);
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return outboundMsgMatcher.match(msg);
    }

    /**
     * @see MessageToMessageEncoder#encode(ChannelHandlerContext, Object, List)
     */
    protected void encode(ChannelHandlerContext ctx, OUTBOUND_IN msg, List<Object> out)
            throws Exception {
        throw new CodecException(getClass().getName() + " must override either encode() or encodeAndClose().");
    }

    /**
     * @see MessageToMessageEncoder#encodeAndClose(ChannelHandlerContext, Object, List)
     */
    protected void encodeAndClose(ChannelHandlerContext ctx, OUTBOUND_IN msg, List<Object> out) throws Exception {
        try (AutoCloseable ignore = autoClosing(msg)) {
            encode(ctx, msg, out);
        }
    }

    /**
     * @see MessageToMessageDecoder#decode(ChannelHandlerContext, Object)
     */
    protected void decode(ChannelHandlerContext ctx, INBOUND_IN msg) throws Exception {
        throw new CodecException(getClass().getName() + " must override either decode() or decodeAndClose().");
    }

    /**
     * @see MessageToMessageDecoder#decodeAndClose(ChannelHandlerContext, Object)
     */
    protected void decodeAndClose(ChannelHandlerContext ctx, INBOUND_IN msg) throws Exception {
        try (AutoCloseable ignore = autoClosing(msg)) {
            decode(ctx, msg);
        }
    }
}
