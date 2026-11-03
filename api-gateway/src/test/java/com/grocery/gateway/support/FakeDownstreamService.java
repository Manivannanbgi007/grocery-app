package com.grocery.gateway.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A tiny HTTP server (built into the JDK) that stands in for a real microservice in gateway tests.
 *
 * It answers EVERY request with 200 and a JSON body describing what it received:
 * <pre>
 * { "success": true, "message": "echo",
 *   "data": { "service": "auth-service", "method": "GET", "path": "/api/v1/auth/me", "userId": "4", "userRole": "CUSTOMER",
 *             "storeId": null, "correlationId": "..." } }
 * </pre>
 * so a test can check exactly what the gateway forwarded.
 */
public final class FakeDownstreamService implements AutoCloseable {

    private final HttpServer server;

    private FakeDownstreamService(HttpServer server) {
        this.server = server;
    }

    public static FakeDownstreamService start() {
        return start("fake-service");
    }

    /** @param serviceName returned in every answer, so a test can see which service received a request */
    public static FakeDownstreamService start(String serviceName) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);   // 0 = any free port
            server.createContext("/", exchange -> echo(serviceName, exchange));
            server.start();
            return new FakeDownstreamService(server);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void echo(String serviceName, HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();   // read and ignore the request body
        Headers headers = exchange.getRequestHeaders();

        String json = """
                {"success":true,"message":"echo","data":{"service":"%s","method":"%s","path":"%s","userId":%s,"userRole":%s,"storeId":%s,"correlationId":%s,"cookie":%s}}"""
                .formatted(
                        serviceName,
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        jsonString(headers.getFirst("X-User-Id")),
                        jsonString(headers.getFirst("X-User-Role")),
                        jsonString(headers.getFirst("X-Store-Id")),
                        jsonString(headers.getFirst("X-Correlation-Id")),
                        jsonString(headers.getFirst("Cookie")));

        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (exchange.getRequestURI().getPath().equals("/api/v1/auth/refresh")) {
            // like auth-service: the new refresh token in a cookie (the gateway must pass it through unchanged)
            exchange.getResponseHeaders().set("Set-Cookie", "refresh_token=new-token; Path=/api/v1/auth; HttpOnly; SameSite=Strict");
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
