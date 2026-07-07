package com.priceradar.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class LocalHttpStub implements AutoCloseable {

    private final HttpServer server;

    private LocalHttpStub(HttpServer server) {
        this.server = server;
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
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext(path, exchange -> respond(exchange, statusCode, body));
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (var response = exchange.getResponseBody()) {
            response.write(body);
        } finally {
            exchange.close();
        }
    }
}
