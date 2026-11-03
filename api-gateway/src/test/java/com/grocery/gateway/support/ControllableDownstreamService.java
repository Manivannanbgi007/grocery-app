package com.grocery.gateway.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake microservice whose behaviour a test can change: answer normally, fail with a status, fail only the first
 * few requests, or answer slowly. It counts the requests it received, so a test can see retries (or their absence).
 *
 * <pre>
 * service.failFirst(2, 503);   // two 503s, then 200
 * service.respondWith(500);    // always 500
 * service.respondAfter(Duration.ofSeconds(3));   // slower than the gateway's response timeout
 * service.reset();             // normal again: 200 at once
 * </pre>
 */
public final class ControllableDownstreamService implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile int failuresLeft = 0;
    private volatile int failureStatus = 503;
    private volatile Duration delay = Duration.ZERO;

    private ControllableDownstreamService(HttpServer server) {
        this.server = server;
    }

    public static ControllableDownstreamService start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            ControllableDownstreamService service = new ControllableDownstreamService(server);
            server.createContext("/", service::answer);
            server.setExecutor(Executors.newCachedThreadPool());   // several requests at the same time (bulkhead test)
            server.start();
            return service;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void respondWith(int status) {
        this.status = status;
    }

    public void failFirst(int times, int status) {
        this.failuresLeft = times;
        this.failureStatus = status;
    }

    public void respondAfter(Duration delay) {
        this.delay = delay;
    }

    public int requestCount() {
        return requests.get();
    }

    public void reset() {
        status = 200;
        failuresLeft = 0;
        delay = Duration.ZERO;
        requests.set(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        requests.incrementAndGet();
        sleep(delay);

        int answerStatus = status;
        synchronized (this) {
            if (failuresLeft > 0) {
                failuresLeft--;
                answerStatus = failureStatus;
            }
        }
        String json = """
                {"success":%s,"message":"downstream answered %d","data":null}""".formatted(answerStatus < 400, answerStatus);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(answerStatus, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        } catch (IOException ex) {
            // the gateway gave up waiting (timeout test): nothing to do
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
