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

package io.netty5.buffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BufferUtilTest {

    @Test
    void testReverseUnsignedShort() {
        assertEquals(0x00FF00, BufferUtil.reverseUnsignedShort(0x0000FF));
        assertEquals(0x0000FF, BufferUtil.reverseUnsignedShort(0x00FF00));
    }

    @Test
    void testReverseMedium() {
        assertEquals(0xFFFF0000, BufferUtil.reverseMedium(0x000000FF));
        assertEquals(0x000000FF, BufferUtil.reverseMedium(0xFFFF0000));
    }

    @Test
    void testReverseUnsignedMedium() {
        assertEquals(0xFF0000, BufferUtil.reverseUnsignedMedium(0x0000FF));
        assertEquals(0x0000FF, BufferUtil.reverseUnsignedMedium(0xFF0000));
    }

    @Test
    void testReverseUnsignedInt() {
        assertEquals(0xFF000000L, BufferUtil.reverseUnsignedInt(0x000000FFL));
        assertEquals(0x000000FFL, BufferUtil.reverseUnsignedInt(0xFF000000L));
    }

}
