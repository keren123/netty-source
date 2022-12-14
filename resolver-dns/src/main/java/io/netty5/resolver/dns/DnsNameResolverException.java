/*
 * Copyright 2015 The Netty Project
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
package io.netty5.resolver.dns;

import io.netty5.handler.codec.dns.DnsQuestion;

import java.net.InetSocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RuntimeException} raised when {@link DnsNameResolver} failed to perform a successful query.
 */
public class DnsNameResolverException extends RuntimeException {

    private static final long serialVersionUID = -8826717909627131850L;

    private final InetSocketAddress remoteAddress;
    private final DnsQuestion question;

    public DnsNameResolverException(InetSocketAddress remoteAddress, DnsQuestion question, String message) {
        super(message, null, true, false);
        this.remoteAddress = validateRemoteAddress(remoteAddress);
        this.question = validateQuestion(question);
    }

    public DnsNameResolverException(
            InetSocketAddress remoteAddress, DnsQuestion question, String message, Throwable cause) {
        super(message, cause, true, false);
        this.remoteAddress = validateRemoteAddress(remoteAddress);
        this.question = validateQuestion(question);
    }

    private static InetSocketAddress validateRemoteAddress(InetSocketAddress remoteAddress) {
        return requireNonNull(remoteAddress, "remoteAddress");
    }

    private static DnsQuestion validateQuestion(DnsQuestion question) {
        return requireNonNull(question, "question");
    }

    /**
     * Returns the {@link InetSocketAddress} of the DNS query that has failed.
     */
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    /**
     * Returns the {@link DnsQuestion} of the DNS query that has failed.
     */
    public DnsQuestion question() {
        return question;
    }
}
