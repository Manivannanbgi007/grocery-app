package com.grocery.report.service.impl;

import com.grocery.events.delivery.PartnerKycReviewed;
import com.grocery.events.inventory.StockChanged;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreRegistered;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.events.user.UserRegistered;
import com.grocery.report.config.ReportProperties;
import com.grocery.report.entity.OrderFact;
import com.grocery.report.entity.OrderItemFact;
import com.grocery.report.entity.PartnerFact;
import com.grocery.report.entity.StockFact;
import com.grocery.report.entity.StoreFact;
import com.grocery.report.entity.UserFact;
import com.grocery.report.enums.ReportOrderStatus;
import com.grocery.report.exception.ResourceNotFoundException;
import com.grocery.report.repository.OrderFactRepository;
import com.grocery.report.repository.OrderItemFactRepository;
import com.grocery.report.repository.PartnerFactRepository;
import com.grocery.report.repository.StockFactRepository;
import com.grocery.report.repository.StoreFactRepository;
import com.grocery.report.repository.UserFactRepository;
import com.grocery.report.service.ProcessedEventService;
import com.grocery.report.service.ReportProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReportProjectionServiceImpl implements ReportProjectionService {

    private static final String ACTIVE = "ACTIVE";

    private final ProcessedEventService processedEventService;
    private final OrderFactRepository orderFactRepository;
    private final OrderItemFactRepository orderItemFactRepository;
    private final StoreFactRepository storeFactRepository;
    private final UserFactRepository userFactRepository;
    private final StockFactRepository stockFactRepository;
    private final PartnerFactRepository partnerFactRepository;
    private final ReportProperties reportProperties;

    @Override
    public void onUserRegistered(UserRegistered event) {
        if (isNew(event.getEventId(), "UserRegistered") && !userFactRepository.existsById(event.getUserId())) {
            userFactRepository.save(new UserFact(event.getUserId(), event.getRole(), event.getOccurredAt()));
        }
    }

    @Override
    public void onStoreRegistered(StoreRegistered event) {
        if (isNew(event.getEventId(), "StoreRegistered") && !storeFactRepository.existsById(event.getStoreId())) {
            storeFactRepository.save(new StoreFact(event.getStoreId(), event.getStoreName(), event.getOwnerUserId(), "PENDING"));
        }
    }

    @Override
    public void onStoreApproved(StoreApproved event) {
        if (!isNew(event.getEventId(), "StoreApproved")) {
            return;
        }
        StoreFact store = storeFactRepository.findById(event.getStoreId())
                .orElseGet(() -> new StoreFact(event.getStoreId(), event.getStoreName(), event.getOwnerUserId(), ACTIVE));
        store.setName(event.getStoreName());
        store.setStatus(ACTIVE);
        storeFactRepository.save(store);
    }

    @Override
    public void onStoreStatusChanged(StoreStatusChanged event) {
        if (isNew(event.getEventId(), "StoreStatusChanged")) {
            storeFactRepository.findById(event.getStoreId()).ifPresent(store -> store.setStatus(event.getStatus()));
        }
    }

    @Override
    public void onOrderCreated(OrderCreated event) {
        if (!isNew(event.getEventId(), "OrderCreated") || orderFactRepository.existsById(event.getOrderId())) {
            return;
        }
        Instant placedAt = event.getOccurredAt();
        orderFactRepository.save(OrderFact.placed(event.getOrderId(), event.getCustomerUserId(), event.getStoreId(),
                event.getPaymentMethod(), money(event.getItemsTotal()), money(event.getCouponDiscount()),
                money(event.getDeliveryFee()), money(event.getTotalAmount()),
                event.getCommissionPercentage() == null ? null : money(event.getCommissionPercentage()),
                placedAt, dayOf(placedAt)));
        event.getItems().forEach(line -> orderItemFactRepository.save(new OrderItemFact(event.getOrderId(), event.getStoreId(),
                line.getProductId(), line.getProductName(), line.getQuantity(),
                money(line.getUnitPrice()).multiply(BigDecimal.valueOf(line.getQuantity())))));
    }

    @Override
    public void onOrderConfirmed(OrderConfirmed event) {
        if (isNew(event.getEventId(), "OrderConfirmed")) {
            findOrder(event.getOrderId()).confirm();
        }
    }

    @Override
    public void onOrderDelivered(OrderDelivered event) {
        if (isNew(event.getEventId(), "OrderDelivered")) {
            findOrder(event.getOrderId()).deliver(event.getOccurredAt(), dayOf(event.getOccurredAt()));
        }
    }

    @Override
    public void onOrderEnded(String eventId, String eventType, Long orderId, ReportOrderStatus endStatus, Instant endedAt) {
        if (isNew(eventId, eventType)) {
            findOrder(orderId).end(endStatus, endedAt);
        }
    }

    @Override
    public void onPaymentConfirmed(PaymentConfirmed event) {
        if (isNew(event.getEventId(), "PaymentConfirmed")) {
            findOrder(event.getOrderId()).paymentReceived(event.getPaymentMethod());
        }
    }

    @Override
    public void onRefundCompleted(RefundCompleted event) {
        if (isNew(event.getEventId(), "RefundCompleted")) {
            findOrder(event.getOrderId()).refunded();
        }
    }

    @Override
    public void onStockChanged(StockChanged event) {
        // No processed_event row: the stock version already makes this idempotent (and keeps the table small)
        StockFact stock = stockFactRepository.findById(event.getProductId()).orElseGet(() -> new StockFact(event.getProductId()));
        if (stock.apply(event.getStoreId(), event.getAvailableQuantity(), event.getStockVersion())) {
            stockFactRepository.save(stock);
        }
    }

    @Override
    public void onPartnerKycReviewed(PartnerKycReviewed event) {
        if (!isNew(event.getEventId(), "PartnerKycReviewed")) {
            return;
        }
        PartnerFact partner = partnerFactRepository.findById(event.getPartnerUserId())
                .orElseGet(() -> new PartnerFact(event.getPartnerUserId(), event.getFullName(), event.getStatus()));
        partner.setKycStatus(event.getStatus());
        partnerFactRepository.save(partner);
    }

    private boolean isNew(String eventId, String eventType) {
        return processedEventService.markAsProcessed(eventId, eventType);
    }

    /**
     * Events of one order arrive in order (same key, same topic), but payment events come on another topic and may
     * be first. Throwing rolls everything back (also the processed-event mark) and Kafka tries again a moment later.
     */
    private OrderFact findOrder(Long orderId) {
        return orderFactRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " is not known yet (OrderCreated not handled yet) - will retry"));
    }

    private LocalDate dayOf(Instant instant) {
        return instant.atZone(reportProperties.timeZone()).toLocalDate();
    }

    private BigDecimal money(String text) {
        return new BigDecimal(text);
    }
}
