/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.unix;

/**
 * A generic socket option. See <a href="https://linux.die.net/man/2/setsockopt">man setsockopt</a>.
 *
 * @param <T> the value type
 */
public abstract class GenericUnixChannelOption<T> extends UnixChannelOption<T> {

    private final int level;
    private final int optname;

    GenericUnixChannelOption(String name, int level, int optname) {
        super(name);
        this.level = level;
        this.optname = optname;
    }

    /**
     * Returns the level. See <a href="https://linux.die.net/man/2/setsockopt">man setsockopt</a>
     *
     * @return the level.
     */
    public int level() {
        return level;
    }

    /**
     * Returns the optname. See <a href="https://linux.die.net/man/2/setsockopt">man setsockopt</a>
     *
     * @return the level.
     */
    public int optname() {
        return optname;
    }
}
