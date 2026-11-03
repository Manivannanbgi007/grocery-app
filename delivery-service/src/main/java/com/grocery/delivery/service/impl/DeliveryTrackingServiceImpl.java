package com.grocery.delivery.service.impl;

import com.grocery.delivery.config.TrackingProperties;
import com.grocery.delivery.dto.common.GeoPoint;
import com.grocery.delivery.dto.request.LocationUpdateRequest;
import com.grocery.delivery.dto.response.LiveLocation;
import com.grocery.delivery.dto.response.LocationUpdateResponse;
import com.grocery.delivery.dto.response.TrackingResponse;
import com.grocery.delivery.dto.response.TrackingRoute;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.exception.BusinessException;
import com.grocery.delivery.exception.ResourceNotFoundException;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.service.DeliveryTrackingService;
import com.grocery.delivery.tracking.LiveLocationStore;
import com.grocery.delivery.tracking.LiveTracking;
import com.grocery.delivery.tracking.LiveTrackingStreams;
import com.grocery.delivery.tracking.RoutingClient;
import com.grocery.delivery.tracking.TrackingBroadcaster;
import com.grocery.delivery.tracking.TrackingSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Who may do what:
 * - a partner sends locations only for a delivery assigned to THEM, and only while it is tracked (accepted, picked up,
 *   out for delivery). Another partner's delivery looks like it does not exist (404).
 * - a customer sees only the delivery of THEIR order (another customer's order: 404, never "forbidden", so nobody can
 *   find out which order ids exist).
 * The API gateway already checked the token and the role (DELIVERY_PARTNER for /api/v1/delivery/**, CUSTOMER here).
 */
@Service
@RequiredArgsConstructor
public class DeliveryTrackingServiceImpl implements DeliveryTrackingService {

    /** A phone's clock may be a little ahead of the server's. */
    private static final Duration ALLOWED_CLOCK_DIFFERENCE = Duration.ofMinutes(1);

    private final DeliveryRepository deliveryRepository;
    private final LiveLocationStore liveLocationStore;
    private final TrackingSnapshotFactory snapshotFactory;
    private final TrackingBroadcaster broadcaster;
    private final LiveTrackingStreams liveTrackingStreams;
    private final RoutingClient routingClient;
    private final TrackingProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    /**
     * Called about every 2 seconds per partner on the way. Not @Transactional on purpose: the two reads (the delivery,
     * and the partner in the snapshot) are short repository calls of their own, so no database connection is held
     * while Redis and the routing server are asked. The location itself goes to Redis, never to MySQL.
     */
    @Override
    public LocationUpdateResponse updateLocation(Long partnerUserId, Long deliveryId, LocationUpdateRequest request) {
        Delivery delivery = deliveryRepository.findByIdAndPartnerUserId(deliveryId, partnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId));
        if (!delivery.getStatus().isTracked()) {
            count("refused");
            throw new BusinessException(delivery.getStatus().isOver()
                    ? "This delivery is " + delivery.getStatus() + ": location sharing has stopped"
                    : "Location sharing starts when you accept the delivery");
        }
        Instant now = Instant.now(clock);
        checkPosition(request, now);

        Optional<LiveTracking> previous = liveLocationStore.find(deliveryId);
        if (previous.isPresent() && shouldSkip(previous.get().location(), request, now)) {
            count("skipped");
            return new LocationUpdateResponse(false, previous.get().route());
        }

        LiveLocation location = new LiveLocation(request.latitude(), request.longitude(), request.accuracyMeters(),
                request.recordedAt(), now);
        TrackingRoute route = routeFrom(delivery, location, previous.map(LiveTracking::route).orElse(null), now);
        LiveTracking tracking = new LiveTracking(location, route);
        liveLocationStore.save(deliveryId, tracking);
        broadcaster.publish(snapshotFactory.build(delivery, tracking));
        count("stored");
        return new LocationUpdateResponse(true, route);
    }

    @Override
    @Transactional(readOnly = true)
    public TrackingResponse getPartnerTracking(Long partnerUserId, Long deliveryId) {
        return snapshotFactory.build(deliveryRepository.findByIdAndPartnerUserId(deliveryId, partnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId)));
    }

    @Override
    @Transactional(readOnly = true)
    public TrackingResponse getCustomerTracking(Long customerUserId, Long orderId) {
        return snapshotFactory.build(findCustomerDelivery(customerUserId, orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public SseEmitter openCustomerStream(Long customerUserId, Long orderId) {
        return liveTrackingStreams.open(snapshotFactory.build(findCustomerDelivery(customerUserId, orderId)));
    }

    /** Refuses positions that would mislead the customer: from the future, too old, or too imprecise. */
    private void checkPosition(LocationUpdateRequest request, Instant now) {
        if (request.recordedAt().isAfter(now.plus(ALLOWED_CLOCK_DIFFERENCE))) {
            count("refused");
            throw new BusinessException("The location's time is in the future: please check the phone's date and time");
        }
        if (request.recordedAt().isBefore(now.minus(properties.maxLocationAge()))) {
            count("refused");
            throw new BusinessException("This location is older than %d minutes and was not used"
                    .formatted(properties.maxLocationAge().toMinutes()));
        }
        if (request.accuracyMeters() != null && request.accuracyMeters() > properties.maxAccuracyMeters()) {
            count("refused");
            throw new BusinessException("The location is not precise enough (about %d m). Please turn on GPS / precise location"
                    .formatted(Math.round(request.accuracyMeters())));
        }
    }

    /**
     * Skipped (not an error): a newer position is already stored (a phone that was offline sends old ones late), or it
     * came too soon after the last stored one.
     */
    private boolean shouldSkip(LiveLocation last, LocationUpdateRequest request, Instant now) {
        return !request.recordedAt().isAfter(last.recordedAt())
                || Duration.between(last.receivedAt(), now).compareTo(properties.minUpdateInterval()) < 0;
    }

    /**
     * The route to the next stop: the store before pick-up, the customer after. Reused while the partner is near where
     * it was computed and it is recent (asking the router on every update would be slow and wasteful).
     */
    private TrackingRoute routeFrom(Delivery delivery, LiveLocation location, TrackingRoute previous, Instant now) {
        TrackingRoute.Target target = delivery.getStatus() == DeliveryStatus.ACCEPTED
                ? TrackingRoute.Target.PICKUP : TrackingRoute.Target.DESTINATION;
        GeoPoint to = target == TrackingRoute.Target.PICKUP
                ? GeoPoint.of(delivery.getPickupLatitude(), delivery.getPickupLongitude())
                : GeoPoint.of(delivery.getDropLatitude(), delivery.getDropLongitude());
        if (to == null || !routingClient.isEnabled()) {
            return null;
        }
        GeoPoint from = new GeoPoint(location.latitude(), location.longitude());
        TrackingProperties.Routing settings = properties.routing();
        boolean previousStillGood = previous != null
                && previous.target() == target
                && previous.from().distanceMetersTo(from) < settings.recalculateAfterMeters()
                && previous.computedAt().plus(settings.recalculateAfter()).isAfter(now);
        if (previousStillGood) {
            return previous;
        }
        return routingClient.findRoute(from, to, target, now).orElse(null);
    }

    private Delivery findCustomerDelivery(Long customerUserId, Long orderId) {
        return deliveryRepository.findByOrderIdAndCustomerUserId(orderId, customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No delivery for order " + orderId + " yet: tracking starts when the order is ready for pickup"));
    }

    private void count(String result) {
        meterRegistry.counter("delivery.tracking.location.updates", "result", result).increment();
    }
}
