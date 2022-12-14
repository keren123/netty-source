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

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.concurrent.PromiseCombiner;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.TypeParameterMatcher;

import java.util.List;

import static io.netty5.util.internal.SilentDispose.autoClosing;

/**
 * {@link ChannelHandler} which encodes from one message to another message
 *
 * For example here is an implementation which decodes an {@link Integer} to an {@link String}.
 *
 * <pre>{@code
 *     public class IntegerToStringEncoder extends
 *             MessageToMessageEncoder<Integer> {
 *
 *         @Override
 *         public void encode(ChannelHandlerContext ctx, Integer message, List<Object> out)
 *                 throws Exception {
 *             out.add(message.toString());
 *         }
 *     }
 * }</pre>
 *
 * Note that messages passed to {@link #encode(ChannelHandlerContext, Object, List)} will be
 * {@linkplain Resource#dispose(Object) disposed of} automatically.
 * <p>
 * To take control of the message lifetime, you should instead override the
 * {@link #encodeAndClose(ChannelHandlerContext, Object, List)} method.
 * <p>
 * Do not override both.
 */
public abstract class MessageToMessageEncoder<I> extends ChannelHandlerAdapter {

    private final TypeParameterMatcher matcher;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     */
    protected MessageToMessageEncoder() {
        matcher = TypeParameterMatcher.find(this, MessageToMessageEncoder.class, "I");
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The type of messages to match and so encode
     */
    protected MessageToMessageEncoder(Class<? extends I> outboundMessageType) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        CodecOutputList out = null;
        try {
            if (acceptOutboundMessage(msg)) {
                out = CodecOutputList.newInstance();
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                Promise<Void> promise = ctx.newPromise();
                try {
                    encodeAndClose(ctx, cast, out);

                    if (out.isEmpty()) {
                        throw new EncoderException(
                                StringUtil.simpleClassName(this) + " must produce at least one message.");
                    }
                } finally {
                    if (out.size() == 1) {
                        ctx.write(out.getUnsafe(0)).cascadeTo(promise);
                    } else {
                        writePromiseCombiner(ctx, out, promise);
                    }
                }
                return promise.asFuture();
            } else {
                return ctx.write(msg);
            }
        } catch (EncoderException e) {
            return ctx.newFailedFuture(e);
        } catch (Throwable t) {
            return ctx.newFailedFuture(new EncoderException(
                    "Unhandled exception in encoder " + getClass().getName(), t));
        } finally {
            if (out != null) {
                out.recycle();
            }
        }
    }

    private static void writePromiseCombiner(ChannelHandlerContext ctx, CodecOutputList out, Promise<Void> promise) {
        final PromiseCombiner combiner = new PromiseCombiner(ctx.executor());
        for (int i = 0; i < out.size(); i++) {
            combiner.add(ctx.write(out.getUnsafe(i)));
        }
        combiner.finish(promise);
    }

    /**
     * Encode from one message to another. This method will be called for each written message that can be handled
     * by this encoder.
     * <p>
     * The message will be {@linkplain Resource#dispose(Object) disposed of} after this call.
     * <p>
     * Subclasses that wish to sometimes pass messages through, should instead override the
     * {@link #encodeAndClose(ChannelHandlerContext, Object, List)} method.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to.
     * @param msg           the message to encode to another one.
     * @param out           the {@link List} into which produced output messages should be added.
     * @throws Exception    is thrown if an error occurs.
     */
    protected void encode(ChannelHandlerContext ctx, I msg, List<Object> out) throws Exception {
        throw new CodecException(getClass().getName() + " must override either encode() or encodeAndClose().");
    }

    /**
     * Encode from one message to another. This method will be called for each written message that can be handled
     * by this encoder.
     * <p>
     * The message will not be automatically {@linkplain Resource#dispose(Object) disposed of} after this call.
     * Instead, the responsibility of ensuring that messages are disposed of falls upon the implementor of this method.
     * <p>
     * Subclasses that wish to have incoming messages automatically disposed of should instead override the
     * {@link #encode(ChannelHandlerContext, Object, List)} method.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to.
     * @param msg           the message to encode to another one.
     * @param out           the {@link List} into which produced output messages should be added.
     * @throws Exception    is thrown if an error occurs.
     */
    protected void encodeAndClose(ChannelHandlerContext ctx, I msg, List<Object> out) throws Exception {
        try (AutoCloseable ignore = autoClosing(msg)) {
            encode(ctx, msg, out);
        }
    }
}
