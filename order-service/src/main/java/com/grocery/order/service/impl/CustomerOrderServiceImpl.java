package com.grocery.order.service.impl;

import com.grocery.order.client.CartClient;
import com.grocery.order.client.CustomerClient;
import com.grocery.order.client.StoreClient;
import com.grocery.order.client.dto.CheckoutSummary;
import com.grocery.order.client.dto.CustomerAddress;
import com.grocery.order.client.dto.StoreInfo;
import com.grocery.order.config.OrderProperties;
import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.request.CancelOrderRequest;
import com.grocery.order.dto.request.PlaceOrderRequest;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.exception.BusinessException;
import com.grocery.order.exception.CheckoutChangedException;
import com.grocery.order.exception.CheckoutNotReadyException;
import com.grocery.order.exception.DuplicateResourceException;
import com.grocery.order.exception.ResourceNotFoundException;
import com.grocery.order.mapper.OrderEventMapper;
import com.grocery.order.mapper.OrderMapper;
import com.grocery.order.messaging.OutboxEventPublisher;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.CustomerOrderService;
import com.grocery.order.service.InvoiceService;
import com.grocery.order.service.OrderCanceller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOrderServiceImpl implements CustomerOrderService {

    private static final String DEFAULT_CANCEL_REASON = "Cancelled by the customer";
    /** The same cart ordered again within this time is taken for a double order (a second click, a second tab) */
    static final Duration SAME_CART_WINDOW = Duration.ofMinutes(2);

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final CustomerClient customerClient;
    private final StoreClient storeClient;
    private final OrderMapper orderMapper;
    private final OrderEventMapper orderEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OrderCanceller orderCanceller;
    private final OrderProperties orderProperties;
    private final InvoiceService invoiceService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Not @Transactional on purpose: the three calls to other services happen first, WITHOUT a database
     * transaction open (a slow service must not hold a database connection). Only the save runs in a transaction.
     *
     * Never two orders for one request: a repeat with the same Idempotency-Key gets the first order (step 0, before the
     * cart is read: after a timeout the order may exist while its cart is already empty); the same cart ordered again a
     * moment later is refused (step 1); and the order is only placed while the cart is still the summary the customer
     * confirmed (step 1, when the request names one).
     */
    @Override
    public OrderResponse placeOrder(Long customerUserId, PlaceOrderRequest request, String idempotencyKey) {
        // 0. A repeat of a request that already placed its order (a retry after a timeout, a double click)
        if (idempotencyKey != null) {
            Optional<OrderResponse> placedBefore = orderPlacedWith(customerUserId, idempotencyKey);
            if (placedBefore.isPresent()) {
                log.info("Customer id={} repeated the request of order id={}: the same order is returned",
                        customerUserId, placedBefore.get().id());
                return placedBefore.get();
            }
        }

        // 1. The cart, checked against today's catalog (cart-service)
        CheckoutSummary checkout = cartClient.getCheckoutSummary(customerUserId);
        if (!checkout.readyForCheckout()) {
            throw new CheckoutNotReadyException(checkout.problems());
        }
        checkItIsTheConfirmedSummary(request, checkout);
        checkNotOrderedAMomentAgo(customerUserId, checkout);

        // 2. The delivery address (customer-service) and the store (store-service)
        CustomerAddress address = customerClient.findAddress(customerUserId, request.addressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address", request.addressId()));
        StoreInfo store = storeClient.findStore(checkout.storeId())
                .orElseThrow(() -> new BusinessException("The store of your cart no longer exists"));
        checkStoreCanTakeTheOrder(store, address);

        // 3. Save the order AND its OrderCreated event in one transaction (outbox): both or neither
        Order order;
        try {
            order = transactionTemplate.execute(status -> {
                Order newOrder = orderMapper.toNewOrder(customerUserId, checkout, address, store, request);
                newOrder.placedWith(idempotencyKey, checkout.checkoutFingerprint());
                newOrder = orderRepository.save(newOrder);
                outboxEventPublisher.publish(KafkaTopics.ORDER_AGGREGATE, newOrder.getId(), orderEventMapper.toOrderCreated(newOrder));
                return newOrder;
            });
        } catch (DataIntegrityViolationException ex) {
            // Two requests with the same key at the same moment: the unique key let only the other one save its order
            Optional<OrderResponse> placedBefore = idempotencyKey == null ? Optional.empty() : orderPlacedWith(customerUserId, idempotencyKey);
            return placedBefore.orElseThrow(() -> ex);
        }
        log.info("Customer id={} placed order id={} at store id={}: {} ({})", customerUserId, order.getId(),
                order.getStoreId(), order.getAmounts().getTotalAmount(), order.getPaymentMethod());
        return orderMapper.toResponse(order);
    }

    /** The order placed earlier with this key, read in a transaction (its items are loaded lazily) */
    private Optional<OrderResponse> orderPlacedWith(Long customerUserId, String idempotencyKey) {
        return transactionTemplate.execute(status -> orderRepository.findByCustomerUserIdAndIdempotencyKey(customerUserId, idempotencyKey)
                .map(orderMapper::toResponse));
    }

    /** With a summary the customer confirmed (the chat's Confirm button): the cart must still be exactly that summary. */
    private static void checkItIsTheConfirmedSummary(PlaceOrderRequest request, CheckoutSummary checkout) {
        String confirmed = request.expectedCheckoutFingerprint();
        if (confirmed != null && !confirmed.equals(checkout.checkoutFingerprint())) {
            throw new CheckoutChangedException();
        }
    }

    /** The same cart became an order a moment ago (and that order is still going): a second order is most likely a mistake. */
    private void checkNotOrderedAMomentAgo(Long customerUserId, CheckoutSummary checkout) {
        if (checkout.checkoutFingerprint() == null) {
            return;
        }
        Instant since = clock.instant().minus(SAME_CART_WINDOW);
        orderRepository.findRecentOrdersOfCart(customerUserId, checkout.checkoutFingerprint(), since).stream()
                .findFirst()
                .ifPresent(order -> {
                    throw new DuplicateResourceException("You placed this order a moment ago (order #" + order.getId() + ").");
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMyOrders(Long customerUserId, OrderStatus status, Pageable pageable) {
        return PageResponse.from(orderRepository.search(customerUserId, null, status, pageable).map(orderMapper::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long customerUserId, Long orderId) {
        return orderMapper.toResponse(findMyOrder(customerUserId, orderId));
    }

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(Long customerUserId, Long orderId, CancelOrderRequest request) {
        Order order = findMyOrder(customerUserId, orderId);
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? DEFAULT_CANCEL_REASON : request.reason().trim();
        orderCanceller.cancel(order, reason, ChangedBy.CUSTOMER);
        return orderMapper.toResponse(order);
    }

    private void checkStoreCanTakeTheOrder(StoreInfo store, CustomerAddress address) {
        if (!store.isActive() || !store.acceptingOrders()) {
            throw new BusinessException(store.name() + " is not taking orders right now");
        }
        if (orderProperties.requireStoreOpen() && !store.openNow()) {
            throw new BusinessException(store.name() + " is closed right now. Please order during its opening hours.");
        }
        if (!store.deliversTo(address.pincode())) {
            throw new BusinessException("%s does not deliver to PIN code %s. Please choose another address."
                    .formatted(store.name(), address.pincode()));
        }
    }

    /** Another customer's order looks like it does not exist. InvoiceService runs its own short transactions. */
    @Override
    public InvoiceFile getMyInvoice(Long customerUserId, Long orderId) {
        return invoiceService.getInvoice(() -> findMyOrder(customerUserId, orderId));
    }

    private Order findMyOrder(Long customerUserId, Long orderId) {
        return orderRepository.findByIdAndCustomerUserId(orderId, customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }
}
