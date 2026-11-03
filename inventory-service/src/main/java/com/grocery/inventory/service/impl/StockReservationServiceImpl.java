package com.grocery.inventory.service.impl;

import com.grocery.events.inventory.UnavailableItem;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderLine;
import com.grocery.inventory.constant.KafkaTopics;
import com.grocery.inventory.entity.Stock;
import com.grocery.inventory.entity.StockReservation;
import com.grocery.inventory.entity.StockReservationItem;
import com.grocery.inventory.mapper.ReservationEventMapper;
import com.grocery.inventory.messaging.OutboxEventPublisher;
import com.grocery.inventory.messaging.StockEventPublisher;
import com.grocery.inventory.repository.StockRepository;
import com.grocery.inventory.repository.StockReservationRepository;
import com.grocery.inventory.service.ProcessedEventService;
import com.grocery.inventory.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationServiceImpl implements StockReservationService {

    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;
    private final ProcessedEventService processedEventService;
    private final StockEventPublisher stockEventPublisher;
    private final ReservationEventMapper reservationEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * All or nothing: first check EVERY item, and only when all are available, reserve them.
     * Two orders reserving the same product at the same moment cannot both win: the second update fails
     * on the Stock @Version, the transaction rolls back and Kafka retries it with the new numbers.
     */
    @Override
    @Transactional
    public void reserveForOrder(OrderCreated event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "OrderCreated")
                || reservationRepository.existsByOrderId(event.getOrderId())) {
            return;
        }
        Map<Long, Integer> wanted = quantitiesByProduct(event.getItems());
        Map<Long, Stock> stocks = stockRepository.findByStoreIdAndProductIdIn(event.getStoreId(), wanted.keySet()).stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));

        // 1. Check everything
        List<UnavailableItem> unavailable = new ArrayList<>();
        wanted.forEach((productId, quantity) -> {
            int available = Optional.ofNullable(stocks.get(productId)).map(Stock::getAvailableQuantity).orElse(0);
            if (available < quantity) {
                unavailable.add(new UnavailableItem(productId, quantity, available));
            }
        });
        if (!unavailable.isEmpty()) {
            outboxEventPublisher.publish(KafkaTopics.INVENTORY_AGGREGATE, event.getOrderId(),
                    reservationEventMapper.toStockReservationFailed(event.getOrderId(), event.getStoreId(), unavailable));
            log.info("Order id={}: not enough stock for {} product(s) - nothing reserved", event.getOrderId(), unavailable.size());
            return;
        }

        // 2. Reserve everything (in product id order, so two transactions always lock rows in the same order)
        wanted.forEach((productId, quantity) -> {
            Stock stock = stocks.get(productId);
            boolean wasLowStock = stock.isLowStock();
            stock.reserve(quantity);
            stockEventPublisher.publishChange(stock, wasLowStock);   // search shows the lower availability
        });
        StockReservation reservation = reservationRepository.save(
                StockReservation.reserve(event.getOrderId(), event.getStoreId(), wanted));
        outboxEventPublisher.publish(KafkaTopics.INVENTORY_AGGREGATE, event.getOrderId(), reservationEventMapper.toStockReserved(reservation));
        log.info("Order id={}: reserved {} product(s)", event.getOrderId(), wanted.size());
    }

    @Override
    @Transactional
    public void releaseForOrder(String eventId, String eventType, Long orderId) {
        if (!processedEventService.markAsProcessed(eventId, eventType)) {
            return;
        }
        Optional<StockReservation> found = reservationRepository.findByOrderId(orderId);
        if (found.isEmpty() || !found.get().isReserved()) {
            // Never reserved (the reservation failed) or already released: nothing to give back
            log.info("{} for order id={}: no held stock to release", eventType, orderId);
            return;
        }
        StockReservation reservation = found.get();
        for (StockReservationItem item : reservation.getItems()) {
            stockRepository.findByProductIdAndStoreId(item.getProductId(), reservation.getStoreId())
                    .ifPresentOrElse(stock -> {
                        boolean wasLowStock = stock.isLowStock();
                        stock.release(item.getQuantity());
                        stockEventPublisher.publishChange(stock, wasLowStock);
                    }, () -> log.warn("Order id={}: stock of product id={} no longer exists", orderId, item.getProductId()));
        }
        reservation.markReleased();
        outboxEventPublisher.publish(KafkaTopics.INVENTORY_AGGREGATE, orderId, reservationEventMapper.toStockReleased(reservation));
        log.info("Order id={} {}: released the held stock", orderId, eventType);
    }

    @Override
    @Transactional
    public void commitForOrder(OrderDelivered event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "OrderDelivered")) {
            return;
        }
        Optional<StockReservation> found = reservationRepository.findByOrderId(event.getOrderId());
        if (found.isEmpty() || !found.get().isReserved()) {
            log.warn("OrderDelivered for order id={} without held stock - ignored", event.getOrderId());
            return;
        }
        StockReservation reservation = found.get();
        for (StockReservationItem item : reservation.getItems()) {
            stockRepository.findByProductIdAndStoreId(item.getProductId(), reservation.getStoreId())
                    .ifPresent(stock -> {
                        boolean wasLowStock = stock.isLowStock();
                        stock.commitReservation(item.getQuantity());
                        stockEventPublisher.publishChange(stock, wasLowStock);
                    });
        }
        reservation.markCommitted();
        log.info("Order id={} delivered: the held units left the store", event.getOrderId());
    }

    /** productId -> total quantity, sorted by productId. */
    private Map<Long, Integer> quantitiesByProduct(List<OrderLine> lines) {
        Map<Long, Integer> quantities = new TreeMap<>();
        lines.forEach(line -> quantities.merge(line.getProductId(), line.getQuantity(), Integer::sum));
        return quantities;
    }
}
