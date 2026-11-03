package com.grocery.delivery.tracking;

import com.grocery.delivery.config.TrackingProperties;
import com.grocery.delivery.dto.common.GeoPoint;
import com.grocery.delivery.dto.response.TrackingRoute;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asks a routing service for the way along the streets and the driving time (for the route line and the arrival time
 * on the tracking map). OpenStreetMap itself only draws the map; routing is a separate service.
 *
 * Works with any OSRM-compatible server (app.tracking.routing.url), e.g. OSRM on your own server with an OpenStreetMap
 * extract of your region. Without a URL it does nothing: findRoute is always empty, the map draws a straight dashed
 * line and shows no arrival time. The public OSRM demo server is NOT meant for an application (no capacity promise).
 *
 * A routing problem never breaks tracking: the location is stored anyway, only without a route.
 *
 *   GET {url}/route/v1/driving/{fromLon},{fromLat};{toLon},{toLat}?overview=simplified&geometries=geojson
 */
@Slf4j
@Component
public class RoutingClient {

    private final TrackingProperties.Routing settings;
    private final RestClient restClient;                       // null when routing is switched off
    private final AtomicBoolean failing = new AtomicBoolean();  // log a broken router once, not on every update

    public RoutingClient(RestClient.Builder restClientBuilder, TrackingProperties properties) {
        this.settings = properties.routing();
        if (!settings.enabled()) {
            this.restClient = null;
            log.info("Routing for the delivery tracking is off (app.tracking.routing.url is empty): no arrival times");
            return;
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(settings.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.timeout());
        this.restClient = restClientBuilder.baseUrl(settings.url()).requestFactory(requestFactory).build();
        log.info("Routing for the delivery tracking: {}", settings.url());
    }

    public boolean isEnabled() {
        return restClient != null;
    }

    /** The route from where the partner is to where they go next; empty when routing is off, fails or finds nothing. */
    public Optional<TrackingRoute> findRoute(GeoPoint from, GeoPoint to, TrackingRoute.Target target, Instant now) {
        if (restClient == null) {
            return Optional.empty();
        }
        try {
            // the coordinates go into the path as they are (not as a {variable}: that would encode ";" as %3B)
            OsrmResponse answer = restClient.get()
                    .uri("/route/v1/driving/" + coordinates(from, to) + "?overview=simplified&geometries=geojson")
                    .retrieve()
                    .body(OsrmResponse.class);
            if (failing.compareAndSet(true, false)) {
                log.info("Routing works again ({})", settings.url());
            }
            if (answer == null || !"Ok".equals(answer.code()) || answer.routes() == null || answer.routes().isEmpty()) {
                return Optional.empty();
            }
            OsrmRoute route = answer.routes().getFirst();
            // OSRM sends [longitude, latitude]; the map (and our API) uses [latitude, longitude]
            List<double[]> points = route.geometry().coordinates().stream()
                    .map(point -> new double[] {point.get(1), point.get(0)})
                    .toList();
            Instant arrival = now.plusSeconds(Math.round(route.duration()));
            return Optional.of(new TrackingRoute(target, route.distance(), route.duration(), arrival, points, from, now));
        } catch (HttpClientErrorException ex) {
            return Optional.empty();   // e.g. 400 "NoRoute": the router works, but cannot reach one of the points
        } catch (RestClientException ex) {
            if (failing.compareAndSet(false, true)) {
                // The cause only (e.g. "java.net.ConnectException"): the exception's own message contains the request
                // URL, and with it the partner's position - locations never go into the logs
                log.warn("Routing failed ({}): {} - tracking goes on without routes until it works again",
                        settings.url(), String.valueOf(ex.getMostSpecificCause()));
            }
            return Optional.empty();
        }
    }

    /** "77.6408,12.9784;77.6412,12.9719" - longitude first, as OSRM expects. */
    private static String coordinates(GeoPoint from, GeoPoint to) {
        return String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.longitude(), from.latitude(), to.longitude(), to.latitude());
    }

    // ----- the parts of OSRM's answer we use -----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmRoute(double distance, double duration, Geometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<List<Double>> coordinates) {
    }
}
