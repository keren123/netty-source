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

package io.netty5.handler.ssl;

import org.conscrypt.OpenSSLProvider;

import javax.net.ssl.SSLEngine;
import java.io.InputStream;
import java.security.Provider;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class SslTestUtils {

    private SslTestUtils() { }

    static Provider conscryptProvider() {
        return new OpenSSLProvider();
    }

    /**
     * Wraps the given {@link SSLEngine} to add extra tests while executing methods if possible / needed.
     */
    static SSLEngine wrapSSLEngineForTesting(SSLEngine engine) {
        if (engine instanceof ReferenceCountedOpenSslEngine) {
            return new OpenSslErrorStackAssertSSLEngine((ReferenceCountedOpenSslEngine) engine);
        }
        return engine;
    }

    public static X509Certificate[] loadCertCollection(String... resourceNames)
            throws Exception {
        CertificateFactory certFactory = CertificateFactory
                .getInstance("X.509");

        X509Certificate[] certCollection = new X509Certificate[resourceNames.length];
        for (int i = 0; i < resourceNames.length; i++) {
            String resourceName = resourceNames[i];
            InputStream is = null;
            try {
                is = SslContextTest.class.getResourceAsStream(resourceName);
                assertNotNull(is, "Cannot find " + resourceName);
                certCollection[i] = (X509Certificate) certFactory
                        .generateCertificate(is);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }
        return certCollection;
    }
}
