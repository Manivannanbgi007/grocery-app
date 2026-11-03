package com.grocery.delivery.tracking;

import com.grocery.delivery.config.TrackingProperties;
import com.grocery.delivery.dto.common.GeoPoint;
import com.grocery.delivery.dto.response.TrackingRoute;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The OSRM call against a fake routing server (the JDK's built-in HTTP server). */
class RoutingClientTest {

    private static final GeoPoint STORE = new GeoPoint(12.9784, 77.6408);
    private static final GeoPoint CUSTOMER = new GeoPoint(12.9719, 77.6412);
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private HttpServer fakeOsrm;

    @AfterEach
    void stopServer() {
        if (fakeOsrm != null) {
            fakeOsrm.stop(0);
        }
    }

    @Test
    void readsTheRoute_turnsOsrmsLongitudeLatitudeAround_andComputesTheArrival() throws IOException {
        AtomicReference<String> askedFor = new AtomicReference<>();
        startFakeOsrm(200, """
                {"code":"Ok","routes":[{"distance":812.4,"duration":145.6,
                  "geometry":{"type":"LineString","coordinates":[[77.6408,12.9784],[77.6410,12.9750],[77.6412,12.9719]]}}]}
                """, askedFor);

        Optional<TrackingRoute> route = client(url()).findRoute(STORE, CUSTOMER, TrackingRoute.Target.DESTINATION, NOW);

        assertThat(askedFor.get()).isEqualTo(
                "/route/v1/driving/77.640800,12.978400;77.641200,12.971900?overview=simplified&geometries=geojson");
        assertThat(route).get().satisfies(r -> {
            assertThat(r.distanceMeters()).isEqualTo(812.4);
            assertThat(r.estimatedArrivalAt()).isEqualTo(NOW.plusSeconds(146));
            assertThat(r.points()).hasSize(3);
            assertThat(r.points().getFirst()).containsExactly(12.9784, 77.6408);   // [latitude, longitude]
            assertThat(r.target()).isEqualTo(TrackingRoute.Target.DESTINATION);
            assertThat(r.from()).isEqualTo(STORE);
        });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void noRouteFound_givesNothing_withoutCallingTheRouterBroken(CapturedOutput log) throws IOException {
        startFakeOsrm(400, """
                {"code":"NoRoute","message":"Impossible route between points"}
                """, new AtomicReference<>());

        assertThat(client(url()).findRoute(STORE, CUSTOMER, TrackingRoute.Target.DESTINATION, NOW)).isEmpty();
        assertThat(log.getOut()).doesNotContain("Routing failed");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aRouterThatIsDown_givesNothing_insteadOfAnError_andTheLogHasNoPosition(CapturedOutput log) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThat(client("http://localhost:" + closedPort).findRoute(STORE, CUSTOMER, TrackingRoute.Target.PICKUP, NOW)).isEmpty();
        assertThat(log.getOut()).contains("Routing failed").doesNotContain("12.978400").doesNotContain("77.640800");
    }

    @Test
    void withoutAUrl_routingIsOff() {
        RoutingClient client = client("");

        assertThat(client.isEnabled()).isFalse();
        assertThat(client.findRoute(STORE, CUSTOMER, TrackingRoute.Target.PICKUP, NOW)).isEmpty();
    }

    private static RoutingClient client(String url) {
        TrackingProperties properties = new TrackingProperties(Duration.ofHours(2), Duration.ofSeconds(2), Duration.ofMinutes(10),
                1000, Duration.ofSeconds(15), Duration.ofMinutes(30), 100,
                new TrackingProperties.Routing(url, Duration.ofSeconds(2), 150, Duration.ofSeconds(60)));
        return new RoutingClient(RestClient.builder(), properties);
    }

    private void startFakeOsrm(int status, String json, AtomicReference<String> askedFor) throws IOException {
        fakeOsrm = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeOsrm.createContext("/", exchange -> {
            askedFor.set(exchange.getRequestURI().toString());
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        fakeOsrm.start();
    }

    private String url() {
        return "http://localhost:" + fakeOsrm.getAddress().getPort();
    }
}
