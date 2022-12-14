/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.BufferClosedException;
import io.netty5.buffer.api.BufferReadOnlyException;
import io.netty5.buffer.api.ByteCursor;
import io.netty5.buffer.api.internal.Statics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class BufferComponentIterationTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void componentCountOfNonCompositeBufferMustBeOne(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countComponents()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void readableComponentCountMustBeOneIfThereAreReadableBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countReadableComponents()).isZero();
            buf.writeByte((byte) 1);
            assertThat(buf.countReadableComponents()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void writableComponentCountMustBeOneIfThereAreWritableBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countWritableComponents()).isOne();
            buf.writeLong(1);
            assertThat(buf.countWritableComponents()).isZero();
        }
    }

    @Test
    public void compositeBufferComponentCountMustBeTransitiveSum() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(8);
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8);
                 Buffer x = allocator.compose(asList(b.send(), c.send()))) {
                buf = allocator.compose(asList(a.send(), x.send()));
            }
            assertThat(buf.countComponents()).isEqualTo(3);
            assertThat(buf.countReadableComponents()).isZero();
            assertThat(buf.countWritableComponents()).isEqualTo(3);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isEqualTo(3);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isOne();
            assertThat(buf.countWritableComponents()).isEqualTo(2);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(2);
            assertThat(buf.countWritableComponents()).isEqualTo(2);
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(2);
            assertThat(buf.countWritableComponents()).isOne();
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(3);
            assertThat(buf.countWritableComponents()).isOne();
            buf.writeInt(1);
            assertThat(buf.countReadableComponents()).isEqualTo(3);
            assertThat(buf.countWritableComponents()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void forEachReadableMustVisitBuffer(Fixture fixture) {
        long value = 0x0102030405060708L;
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer bufBERW = allocator.allocate(8).writeLong(value);
             Buffer bufBERO = allocator.allocate(8).writeLong(value).makeReadOnly()) {
            verifyForEachReadableSingleComponent(fixture, bufBERW);
            verifyForEachReadableSingleComponent(fixture, bufBERO);
        }
    }

    @Test
    public void internalForEachReadableMustVisitAllReadableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Buffer composite;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                a.writeInt(1);
                b.writeInt(2);
                c.writeInt(3);
                composite = allocator.compose(asList(a.send(), b.send(), c.send()));
            }
            var list = new LinkedList<>(List.of(1, 2, 3));
            int count = composite.forEachReadable(0, (index, component) -> {
                var buffer = component.readableBuffer();
                int bufferValue = buffer.getInt();
                int expectedValue = list.pollFirst().intValue();
                assertEquals(expectedValue, bufferValue);
                assertEquals(bufferValue, index + 1);
                assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
                var writableBuffer = Statics.tryGetWritableBufferFromReadableComponent(component);
                if (writableBuffer != null) {
                    int pos = writableBuffer.position();
                    bufferValue = writableBuffer.getInt();
                    assertEquals(expectedValue, bufferValue);
                    assertEquals(bufferValue, index + 1);
                    writableBuffer.put(pos, (byte) 0xFF);
                    assertEquals((byte) 0xFF, writableBuffer.get(pos));
                }
                return true;
            });
            assertEquals(3, count);
            assertThat(list).isEmpty();
        }
    }

    @Test
    public void externalForEachReadableMustVisitAllReadableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Buffer composite;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                a.writeInt(1);
                b.writeInt(2);
                c.writeInt(3);
                composite = allocator.compose(asList(a.send(), b.send(), c.send()));
            }
            var list = new LinkedList<Integer>(List.of(1, 2, 3));
            int index = 0;
            try (var iterator = composite.forEachReadable()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    var buffer = component.readableBuffer();
                    int bufferValue = buffer.getInt();
                    int expectedValue = list.pollFirst().intValue();
                    assertEquals(expectedValue, bufferValue);
                    assertEquals(bufferValue, index + 1);
                    assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
                    var writableBuffer = Statics.tryGetWritableBufferFromReadableComponent(component);
                    if (writableBuffer != null) {
                        int pos = writableBuffer.position();
                        bufferValue = writableBuffer.getInt();
                        assertEquals(expectedValue, bufferValue);
                        assertEquals(bufferValue, index + 1);
                        writableBuffer.put(pos, (byte) 0xFF);
                        assertEquals((byte) 0xFF, writableBuffer.get(pos));
                    }
                    index++;
                }
            }
            assertEquals(3, index);
            assertThat(list).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachReadableMustReturnNegativeCountWhenProcessorReturnsFalse(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            int count = buf.forEachReadable(0, (index, component) -> false);
            assertEquals(-1, count);
        }
    }

    @Test
    public void forEachReadableMustStopIterationWhenProcessorReturnsFalse() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Buffer composite;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                a.writeInt(1);
                b.writeInt(2);
                c.writeInt(3);
                composite = allocator.compose(asList(a.send(), b.send(), c.send()));
            }
            int readPos = composite.readerOffset();
            int writePos = composite.writerOffset();
            var list = new LinkedList<Integer>(List.of(1, 2, 3));
            int count = composite.forEachReadable(0, (index, component) -> {
                var buffer = component.readableBuffer();
                int bufferValue = buffer.getInt();
                assertEquals(list.pollFirst().intValue(), bufferValue);
                assertEquals(bufferValue, index + 1);
                return false;
            });
            assertEquals(-1, count);
            assertThat(list).containsExactly(2, 3);
            assertEquals(readPos, composite.readerOffset());
            assertEquals(writePos, composite.writerOffset());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachReadableOnClosedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            var buf = allocator.allocate(8);
            buf.writeLong(0);
            buf.close();
            assertThrows(BufferClosedException.class, () -> buf.forEachReadable(0, (component, index) -> true));
            assertThrows(BufferClosedException.class, () -> buf.forEachReadable());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachReadableMustAllowCollectingBuffersInArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                buf = allocator.compose(asList(a.send(), b.send(), c.send()));
            }
            int i = 1;
            while (buf.writableBytes() > 0) {
                buf.writeByte((byte) i++);
            }
            ByteBuffer[] buffers = new ByteBuffer[buf.countReadableComponents()];
            buf.forEachReadable(0, (index, component) -> {
                buffers[index] = component.readableBuffer();
                return true;
            });
            i = 1;
            assertThat(buffers.length).isGreaterThanOrEqualTo(1);
            for (ByteBuffer buffer : buffers) {
                while (buffer.hasRemaining()) {
                    assertEquals((byte) i++, buffer.get());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachReadableMustAllowCollectingBuffersInArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(4);
                 Buffer b = allocator.allocate(4);
                 Buffer c = allocator.allocate(4)) {
                buf = allocator.compose(asList(a.send(), b.send(), c.send()));
            }
            int i = 1;
            while (buf.writableBytes() > 0) {
                buf.writeByte((byte) i++);
            }
            ByteBuffer[] buffers = new ByteBuffer[buf.countReadableComponents()];
            try (var iterator = buf.forEachReadable()) {
                int index = 0;
                for (var component = iterator.first(); component != null; component = component.next())  {
                    buffers[index] = component.readableBuffer();
                    index++;
                }
            }
            i = 1;
            assertThat(buffers.length).isGreaterThanOrEqualTo(1);
            for (ByteBuffer buffer : buffers) {
                while (buffer.hasRemaining()) {
                    assertEquals((byte) i++, buffer.get());
                }
            }
            buf.close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachReadableMustExposeByteCursors(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(20)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeLong(0x1112131415161718L);
            assertEquals(0x01020304, buf.readInt());
            try (Buffer actualData = allocator.allocate(buf.readableBytes());
                 Buffer expectedData = allocator.allocate(12)) {
                expectedData.writeInt(0x05060708);
                expectedData.writeInt(0x11121314);
                expectedData.writeInt(0x15161718);

                buf.forEachReadable(0, (i, component) -> {
                    ByteCursor forward = component.openCursor();
                    while (forward.readByte()) {
                        actualData.writeByte(forward.getByte());
                    }
                    return true;
                });

                assertEquals(expectedData.readableBytes(), actualData.readableBytes());
                while (expectedData.readableBytes() > 0) {
                    assertEquals(expectedData.readByte(), actualData.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachReadableMustExposeByteCursors(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(20)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeLong(0x1112131415161718L);
            assertEquals(0x01020304, buf.readInt());
            try (Buffer actualData = allocator.allocate(buf.readableBytes());
                 Buffer expectedData = allocator.allocate(12)) {
                expectedData.writeInt(0x05060708);
                expectedData.writeInt(0x11121314);
                expectedData.writeInt(0x15161718);

                try (var iterator = buf.forEachReadable()) {
                    for (var component = iterator.first(); component != null; component = component.next()) {
                        ByteCursor forward = component.openCursor();
                        while (forward.readByte()) {
                            actualData.writeByte(forward.getByte());
                        }
                    }
                }

                assertEquals(expectedData.readableBytes(), actualData.readableBytes());
                while (expectedData.readableBytes() > 0) {
                    assertEquals(expectedData.readByte(), actualData.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachReadableMustExposeByteCursorsPartial(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(32)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeLong(0x1112131415161718L);
            assertEquals(0x01020304, buf.readInt());
            try (Buffer actualData = allocator.allocate(buf.readableBytes());
                 Buffer expectedData = allocator.allocate(12)) {
                expectedData.writeInt(0x05060708);
                expectedData.writeInt(0x11121314);
                expectedData.writeInt(0x15161718);

                buf.forEachReadable(0, (i, component) -> {
                    ByteCursor forward = component.openCursor();
                    while (forward.readByte()) {
                        actualData.writeByte(forward.getByte());
                    }
                    return true;
                });

                assertEquals(expectedData.readableBytes(), actualData.readableBytes());
                while (expectedData.readableBytes() > 0) {
                    assertEquals(expectedData.readByte(), actualData.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachReadableMustExposeByteCursorsPartial(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(32)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeLong(0x1112131415161718L);
            assertEquals(0x01020304, buf.readInt());
            try (Buffer actualData = allocator.allocate(buf.readableBytes());
                 Buffer expectedData = allocator.allocate(12)) {
                expectedData.writeInt(0x05060708);
                expectedData.writeInt(0x11121314);
                expectedData.writeInt(0x15161718);

                try (var iterator = buf.forEachReadable()) {
                    for (var component = iterator.first(); component != null; component = component.next()) {
                        ByteCursor forward = component.openCursor();
                        while (forward.readByte()) {
                            actualData.writeByte(forward.getByte());
                        }
                    }
                }

                assertEquals(expectedData.readableBytes(), actualData.readableBytes());
                while (expectedData.readableBytes() > 0) {
                    assertEquals(expectedData.readByte(), actualData.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachReadableMustReturnZeroWhenNotReadable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            int count = buf.forEachReadable(0, (index, component) -> {
                fail();
                return true;
            });
            assertEquals(0, count);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachReadableMustReturnNullFirstWhenNotReadable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            try (var iterator = buf.forEachReadable()) {
                assertThat(iterator.first()).isNull();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void forEachWritableMustVisitBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer bufBERW = allocator.allocate(8)) {
            verifyForEachWritableSingleComponent(fixture, bufBERW);
        }
    }

    @Test
    public void internalForEachWritableMustVisitAllWritableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(8);
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8)) {
                buf = allocator.compose(asList(a.send(), b.send(), c.send()));
            }
            buf.forEachWritable(0, (index, component) -> {
                component.writableBuffer().putLong(0x0102030405060708L + 0x1010101010101010L * index);
                return true;
            });
            buf.writerOffset(3 * 8);
            assertEquals(0x0102030405060708L, buf.readLong());
            assertEquals(0x1112131415161718L, buf.readLong());
            assertEquals(0x2122232425262728L, buf.readLong());
        }
    }

    @Test
    public void externalForEachWritableMustVisitAllWritableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Buffer buf;
            try (Buffer a = allocator.allocate(8);
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8)) {
                buf = allocator.compose(asList(a.send(), b.send(), c.send()));
            }
            try (var iterator = buf.forEachWritable()) {
                int index = 0;
                for (var component = iterator.first(); component != null; component = component.next()) {
                    component.writableBuffer().putLong(0x0102030405060708L + 0x1010101010101010L * index);
                    index++;
                }
            }
            buf.writerOffset(3 * 8);
            assertEquals(0x0102030405060708L, buf.readLong());
            assertEquals(0x1112131415161718L, buf.readLong());
            assertEquals(0x2122232425262728L, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableMustReturnNegativeCountWhenProcessorReturnsFalse(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            int count = buf.forEachWritable(0, (index, component) -> false);
            assertEquals(-1, count);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableMustStopIterationWhenProcessorReturnsFalse(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            AtomicInteger counter = new AtomicInteger();
            buf.forEachWritable(0, (index, component) -> {
                counter.incrementAndGet();
                return false;
            });
            assertEquals(1, counter.get());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachWritableChangesMadeToByteBufferComponentMustBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(9)) {
            buf.writeByte((byte) 0xFF);
            AtomicInteger writtenCounter = new AtomicInteger();
            buf.forEachWritable(0, (index, component) -> {
                ByteBuffer buffer = component.writableBuffer();
                while (buffer.hasRemaining()) {
                    buffer.put((byte) writtenCounter.incrementAndGet());
                }
                return true;
            });
            buf.writerOffset(9);
            assertEquals((byte) 0xFF, buf.readByte());
            assertEquals(0x0102030405060708L, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachWritableChangesMadeToByteBufferComponentMustBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(9)) {
            buf.writeByte((byte) 0xFF);
            AtomicInteger writtenCounter = new AtomicInteger();
            try (var iterator = buf.forEachWritable()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    ByteBuffer buffer = component.writableBuffer();
                    while (buffer.hasRemaining()) {
                        buffer.put((byte) writtenCounter.incrementAndGet());
                    }
                }
            }
            buf.writerOffset(9);
            assertEquals((byte) 0xFF, buf.readByte());
            assertEquals(0x0102030405060708L, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachWritableMustReturnZeroWhenNotWritable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            int count = buf.forEachWritable(0, (index, component) -> {
                fail();
                return true;
            });
            assertEquals(0, count);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachWritableMustReturnZeroWhenNotWritable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            try (var iterator = buf.forEachWritable()) {
                assertThat(iterator.first()).isNull();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void changesMadeToByteBufferComponentsInInternalIterationShouldBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            AtomicInteger counter = new AtomicInteger();
            buf.forEachWritable(0, (index, component) -> {
                ByteBuffer buffer = component.writableBuffer();
                while (buffer.hasRemaining()) {
                    buffer.put((byte) counter.incrementAndGet());
                }
                return true;
            });
            buf.writerOffset(buf.capacity());
            for (int i = 0; i < 8; i++) {
                assertEquals((byte) i + 1, buf.getByte(i));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void changesMadeToByteBufferComponentsInExternalIterationShouldBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            AtomicInteger counter = new AtomicInteger();
            try (var iterator = buf.forEachWritable()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    ByteBuffer buffer = component.writableBuffer();
                    while (buffer.hasRemaining()) {
                        buffer.put((byte) counter.incrementAndGet());
                    }
                }
            }
            buf.writerOffset(buf.capacity());
            for (int i = 0; i < 8; i++) {
                assertEquals((byte) i + 1, buf.getByte(i));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableOnClosedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buf = allocator.allocate(8);
            buf.close();
            assertThrows(BufferClosedException.class, () -> buf.forEachWritable(0, (index, component) -> true));
            assertThrows(BufferClosedException.class, () -> buf.forEachWritable());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachWritableOnReadOnlyBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).makeReadOnly()) {
            assertThrows(BufferReadOnlyException.class, () -> buf.forEachWritable(0, (index, component) -> true));
            assertThrows(BufferReadOnlyException.class, () -> buf.forEachWritable());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachWritableMustAllowCollectingBuffersInArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            ByteBuffer[] buffers = new ByteBuffer[buf.countWritableComponents()];
            buf.forEachWritable(0, (index, component) -> {
                buffers[index] = component.writableBuffer();
                return true;
            });
            assertThat(buffers.length).isGreaterThanOrEqualTo(1);
            int i = 1;
            for (ByteBuffer buffer : buffers) {
                while (buffer.hasRemaining()) {
                    buffer.put((byte) i++);
                }
            }
            buf.writerOffset(buf.capacity());
            i = 1;
            while (buf.readableBytes() > 0) {
                assertEquals((byte) i++, buf.readByte());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachWritableMustAllowCollectingBuffersInArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            ByteBuffer[] buffers = new ByteBuffer[buf.countWritableComponents()];
            try (var iterator = buf.forEachWritable()) {
                int index = 0;
                for (var component = iterator.first(); component != null; component = component.next()) {
                    buffers[index] = component.writableBuffer();
                    index++;
                }
            }
            assertThat(buffers.length).isGreaterThanOrEqualTo(1);
            int i = 1;
            for (ByteBuffer buffer : buffers) {
                while (buffer.hasRemaining()) {
                    buffer.put((byte) i++);
                }
            }
            buf.writerOffset(buf.capacity());
            i = 1;
            while (buf.readableBytes() > 0) {
                assertEquals((byte) i++, buf.readByte());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachReadableMustBeAbleToIncrementReaderOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8);
             Buffer target = allocator.allocate(5)) {
            buf.writeLong(0x0102030405060708L);
            int components = buf.forEachReadable(0, (index, component) -> {
                while (target.writableBytes() > 0 && component.readableBytes() > 0) {
                    ByteBuffer byteBuffer = component.readableBuffer();
                    byte value = byteBuffer.get();
                    byteBuffer.clear();
                    target.writeByte(value);
                    assertThrows(IndexOutOfBoundsException.class, () -> component.skipReadableBytes(9));
                    component.skipReadableBytes(0);
                    component.skipReadableBytes(1);
                }
                return target.writableBytes() > 0;
            });
            assertThat(components).isNotEqualTo(0); // May be negative if iteration stops early.
            assertThat(buf.readerOffset()).isEqualTo(5);
            assertThat(buf.readableBytes()).isEqualTo(3);
            assertThat(target.readableBytes()).isEqualTo(5);
            assertThat(target).isEqualTo(allocator.copyOf(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05}));
            assertThat(buf).isEqualTo(allocator.copyOf(new byte[] {0x06, 0x07, 0x08}));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachReadableMustBeAbleToIncrementReaderOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8);
             Buffer target = allocator.allocate(5)) {
            buf.writeLong(0x0102030405060708L);
            try (var iterator = buf.forEachReadable()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    while (target.writableBytes() > 0 && component.readableBytes() > 0) {
                        ByteBuffer byteBuffer = component.readableBuffer();
                        byte value = byteBuffer.get();
                        byteBuffer.clear();
                        target.writeByte(value);
                        var cmp = component; // Capture for lambda.
                        assertThrows(IndexOutOfBoundsException.class, () -> cmp.skipReadableBytes(9));
                        component.skipReadableBytes(0);
                        component.skipReadableBytes(1);
                    }
                }
            }
            assertThat(buf.readerOffset()).isEqualTo(5);
            assertThat(buf.readableBytes()).isEqualTo(3);
            assertThat(target.readableBytes()).isEqualTo(5);
            assertThat(target).isEqualTo(allocator.copyOf(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05}));
            assertThat(buf).isEqualTo(allocator.copyOf(new byte[] {0x06, 0x07, 0x08}));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void internalForEachWritableMustBeAbleToIncrementWriterOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).writeLong(0x0102030405060708L);
             Buffer target = buf.copy()) {
            buf.writerOffset(0); // Prime the buffer with data, but leave the write-offset at zero.
            int components = buf.forEachWritable(0, (index, component) -> {
                while (component.writableBytes() > 0) {
                    ByteBuffer byteBuffer = component.writableBuffer();
                    byte value = byteBuffer.get();
                    byteBuffer.clear();
                    assertThat(value).isEqualTo(target.readByte());
                    assertThrows(IndexOutOfBoundsException.class, () -> component.skipWritableBytes(9));
                    component.skipWritableBytes(0);
                    component.skipWritableBytes(1);
                }
                return true;
            });
            assertThat(components).isGreaterThan(0);
            assertThat(buf.writerOffset()).isEqualTo(8);
            assertThat(target.readerOffset()).isEqualTo(8);
            target.readerOffset(0);
            assertThat(buf).isEqualTo(target);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void externalForEachWritableMustBeAbleToIncrementWriterOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).writeLong(0x0102030405060708L);
             Buffer target = buf.copy()) {
            buf.writerOffset(0); // Prime the buffer with data, but leave the write-offset at zero.
            try (var iterator = buf.forEachWritable()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    while (component.writableBytes() > 0) {
                        ByteBuffer byteBuffer = component.writableBuffer();
                        byte value = byteBuffer.get();
                        byteBuffer.clear();
                        assertThat(value).isEqualTo(target.readByte());
                        var cmp = component; // Capture for lambda.
                        assertThrows(IndexOutOfBoundsException.class, () -> cmp.skipWritableBytes(9));
                        component.skipWritableBytes(0);
                        component.skipWritableBytes(1);
                    }
                }
            }
            assertThat(buf.writerOffset()).isEqualTo(8);
            assertThat(target.readerOffset()).isEqualTo(8);
            target.readerOffset(0);
            assertThat(buf).isEqualTo(target);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void negativeSkipReadableOnReadableComponentMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThat(buf.readInt()).isEqualTo(0x01020304);
            buf.forEachReadable(0, (index, component) -> {
                assertThrows(IllegalArgumentException.class, () -> component.skipReadableBytes(-1));
                return true;
            });

            try (var iterator = buf.forEachReadable()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    var cmp = component; // Capture for lambda.
                    assertThrows(IllegalArgumentException.class, () -> cmp.skipReadableBytes(-1));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void negativeSkipWritableOnWritableComponentMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.forEachWritable(0, (index, component) -> {
                assertThrows(IllegalArgumentException.class, () -> component.skipWritableBytes(-1));
                return true;
            });

            try (var iterator = buf.forEachWritable()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    var cmp = component; // Capture for lambda.
                    assertThrows(IllegalArgumentException.class, () -> cmp.skipWritableBytes(-1));
                }
            }
        }
    }

    public static void verifyForEachReadableSingleComponent(Fixture fixture, Buffer buf) {
        buf.forEachReadable(0, (index, component) -> {
            ByteBuffer buffer = component.readableBuffer();
            assertThat(buffer.position()).isZero();
            assertThat(buffer.limit()).isEqualTo(8);
            assertThat(buffer.capacity()).isEqualTo(8);
            assertEquals(0x0102030405060708L, buffer.getLong());

            if (fixture.isDirect()) {
                assertThat(component.readableNativeAddress()).isNotZero();
            } else {
                assertThat(component.readableNativeAddress()).isZero();
            }

            if (component.hasReadableArray()) {
                byte[] array = component.readableArray();
                byte[] arrayCopy = new byte[component.readableArrayLength()];
                System.arraycopy(array, component.readableArrayOffset(), arrayCopy, 0, arrayCopy.length);
                if (buffer.order() == BIG_ENDIAN) {
                    assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                } else {
                    assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                }
            }

            assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
            return true;
        });

        try (var iterator = buf.forEachReadable()) {
            for (var component = iterator.first(); component != null; component = component.next()) {
                ByteBuffer buffer = component.readableBuffer();
                assertThat(buffer.position()).isZero();
                assertThat(buffer.limit()).isEqualTo(8);
                assertThat(buffer.capacity()).isEqualTo(8);
                assertEquals(0x0102030405060708L, buffer.getLong());

                if (fixture.isDirect()) {
                    assertThat(component.readableNativeAddress()).isNotZero();
                } else {
                    assertThat(component.readableNativeAddress()).isZero();
                }

                if (component.hasReadableArray()) {
                    byte[] array = component.readableArray();
                    byte[] arrayCopy = new byte[component.readableArrayLength()];
                    System.arraycopy(array, component.readableArrayOffset(), arrayCopy, 0, arrayCopy.length);
                    if (buffer.order() == BIG_ENDIAN) {
                        assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                    } else {
                        assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                    }
                }

                assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
            }
        }
    }

    public static void verifyForEachWritableSingleComponent(Fixture fixture, Buffer buf) {
        int roff = buf.readerOffset();
        int woff = buf.writerOffset();
        buf.fill((byte) 0);
        buf.forEachWritable(0, (index, component) -> {
            ByteBuffer buffer = component.writableBuffer();
            assertThat(buffer.position()).isZero();
            assertThat(buffer.limit()).isEqualTo(8);
            assertThat(buffer.capacity()).isEqualTo(8);
            buffer.putLong(0x0102030405060708L);
            buffer.flip();
            assertEquals(0x0102030405060708L, buffer.getLong());
            buf.writerOffset(8);
            assertEquals(0x0102030405060708L, buf.getLong(0));

            if (fixture.isDirect()) {
                assertThat(component.writableNativeAddress()).isNotZero();
            } else {
                assertThat(component.writableNativeAddress()).isZero();
            }

            buf.writerOffset(0);
            if (component.hasWritableArray()) {
                byte[] array = component.writableArray();
                int offset = component.writableArrayOffset();
                byte[] arrayCopy = new byte[component.writableArrayLength()];
                System.arraycopy(array, offset, arrayCopy, 0, arrayCopy.length);
                if (buffer.order() == BIG_ENDIAN) {
                    assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                } else {
                    assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                }
            }

            buffer.put(0, (byte) 0xFF);
            assertEquals((byte) 0xFF, buffer.get(0));
            assertEquals((byte) 0xFF, buf.getByte(0));
            return true;
        });

        buf.resetOffsets().writerOffset(woff).readerOffset(roff);

        buf.fill((byte) 0);
        try (var iterator = buf.forEachWritable()) {
            for (var component = iterator.first(); component != null; component = component.next()) {
                ByteBuffer buffer = component.writableBuffer();
                assertThat(buffer.position()).isZero();
                assertThat(buffer.limit()).isEqualTo(8);
                assertThat(buffer.capacity()).isEqualTo(8);
                buffer.putLong(0x0102030405060708L);
                buffer.flip();
                assertEquals(0x0102030405060708L, buffer.getLong());
                buf.writerOffset(8);
                assertEquals(0x0102030405060708L, buf.getLong(0));

                if (fixture.isDirect()) {
                    assertThat(component.writableNativeAddress()).isNotZero();
                } else {
                    assertThat(component.writableNativeAddress()).isZero();
                }

                buf.writerOffset(0);
                if (component.hasWritableArray()) {
                    byte[] array = component.writableArray();
                    int offset = component.writableArrayOffset();
                    byte[] arrayCopy = new byte[component.writableArrayLength()];
                    System.arraycopy(array, offset, arrayCopy, 0, arrayCopy.length);
                    if (buffer.order() == BIG_ENDIAN) {
                        assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                    } else {
                        assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                    }
                }

                buffer.put(0, (byte) 0xFF);
                assertEquals((byte) 0xFF, buffer.get(0));
                assertEquals((byte) 0xFF, buf.getByte(0));
            }
        }
    }
}
