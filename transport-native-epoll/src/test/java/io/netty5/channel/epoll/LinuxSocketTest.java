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
package io.netty5.channel.epoll;

import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.unix.Errors.NativeIoException;
import io.netty5.channel.unix.Socket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class LinuxSocketTest {
    @BeforeAll
    public static void loadJNI() {
        Epoll.ensureAvailability();
    }

    @Test
    public void testBindNonIpv6SocketToInet6AddressThrows() throws Exception {
        LinuxSocket socket = LinuxSocket.newSocketStream(false);
        try {
            assertThrows(IOException.class, () -> socket.bind(new InetSocketAddress(InetAddress.getByAddress(
                    new byte[]{'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1'}), 0)));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testConnectNonIpv6SocketToInet6AddressThrows() throws Exception {
        LinuxSocket socket = LinuxSocket.newSocketStream(false);
        try {
            assertThrows(IOException.class,
                () -> socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{
                    '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1'}), 1234)));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testUnixDomainSocketTooLongPathFails() throws IOException {
        // Most systems has a limit for UDS path of 108, 255 is generally too long.
        StringBuilder socketPath = new StringBuilder("/tmp/");
        while (socketPath.length() < 255) {
            socketPath.append(UUID.randomUUID());
        }

        final DomainSocketAddress domainSocketAddress = new DomainSocketAddress(
            socketPath.toString());
        final Socket socket = Socket.newSocketDomain();
        try {
            Exception exception = assertThrows(NativeIoException.class, () -> {
                socket.bind(domainSocketAddress);
            });
            Assertions.assertTrue(exception.getMessage().contains("too long"));
        } finally {
            socket.close();
        }
    }
}
