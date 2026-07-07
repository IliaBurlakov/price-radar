package com.priceradar.testsupport;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Blocks external HTTP clients that honor the JVM default {@link ProxySelector}.
 * Loopback traffic remains available for local provider stubs.
 */
public final class ExternalNetworkBlocker implements BeforeAllCallback, AfterAllCallback {

    private ProxySelector previousSelector;

    @Override
    public void beforeAll(ExtensionContext context) {
        previousSelector = ProxySelector.getDefault();
        ProxySelector.setDefault(new LoopbackOnlyProxySelector());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        ProxySelector.setDefault(previousSelector);
    }

    private static final class LoopbackOnlyProxySelector extends ProxySelector {

        @Override
        public List<Proxy> select(URI uri) {
            String host = uri.getHost();
            if (isLoopback(host)) {
                return List.of(Proxy.NO_PROXY);
            }
            throw new AssertionError("Automated tests must not access external host: " + host);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
            throw new AssertionError("Local test connection failed: " + uri, exception);
        }

        private static boolean isLoopback(String host) {
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || "0:0:0:0:0:0:0:1".equals(host);
        }
    }
}
