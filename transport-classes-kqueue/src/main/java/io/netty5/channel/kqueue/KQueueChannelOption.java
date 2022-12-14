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
package io.netty5.channel.kqueue;

import io.netty5.channel.ChannelOption;
import io.netty5.channel.unix.UnixChannelOption;
import io.netty5.util.internal.UnstableApi;

@UnstableApi
public final class KQueueChannelOption<T> extends UnixChannelOption<T> {
    public static final ChannelOption<Integer> SO_SNDLOWAT = valueOf(KQueueChannelOption.class, "SO_SNDLOWAT");
    public static final ChannelOption<Boolean> TCP_NOPUSH = valueOf(KQueueChannelOption.class, "TCP_NOPUSH");
    public static final ChannelOption<AcceptFilter> SO_ACCEPTFILTER =
            valueOf(KQueueChannelOption.class, "SO_ACCEPTFILTER");

    @SuppressWarnings({ "unused", "deprecation" })
    private KQueueChannelOption() {
    }
}
