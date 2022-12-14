/*
 * Copyright 2018 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.channel.unix.IovArray;

import java.io.IOException;

/**
 * Registration with an {@link EpollHandler}.
 */
interface EpollRegistration {

    /**
     * Update the registration as some flags did change.
     */
    void update() throws IOException;

    /**
     * Remove the registration. No more IO will be handled for it.
     */
    void remove() throws IOException;

    /**
     * Returns an {@link IovArray} that can be used for {@code writev}.
     */
    IovArray cleanIovArray();

    /**
     * Returns a {@link NativeDatagramPacketArray} that can used for {@code sendmmsg}.
     */
    NativeDatagramPacketArray cleanDatagramPacketArray();
}
