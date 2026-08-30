package com.priceradar.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalHttpStub implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger requestCount;
    private final AtomicReference<String> lastRequestBody;
    private final AtomicReference<URI> lastRequestUri;

    private LocalHttpStub(HttpServer server) {
        this.server = server;
        this.requestCount = new AtomicInteger();
        this.lastRequestBody = new AtomicReference<>();
        this.lastRequestUri = new AtomicReference<>();
    }

    public static LocalHttpStub start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
            return new LocalHttpStub(server);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start local HTTP stub", exception);
        }
    }

    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public void stub(String path, int statusCode, String responseBody) {
        stub(path, statusCode, responseBody, Map.of());
    }

    public void stub(
            String path,
            int statusCode,
            String responseBody,
            Map<String, String> responseHeaders
    ) {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext(path, exchange -> {
            requestCount.incrementAndGet();
            lastRequestUri.set(exchange.getRequestURI());
            lastRequestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            respond(exchange, statusCode, body, responseHeaders);
        });
    }

    public int requestCount() {
        return requestCount.get();
    }

    public String lastRequestBody() {
        return lastRequestBody.get();
    }

    public URI lastRequestUri() {
        return lastRequestUri.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void respond(
            HttpExchange exchange,
            int statusCode,
            byte[] body,
            Map<String, String> responseHeaders
    ) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(statusCode, body.length);
        try (var response = exchange.getResponseBody()) {
            response.write(body);
        } finally {
            exchange.close();
        }
    }
}
