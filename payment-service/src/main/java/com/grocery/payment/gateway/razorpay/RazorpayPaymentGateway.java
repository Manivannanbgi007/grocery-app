package com.grocery.payment.gateway.razorpay;

import com.grocery.payment.entity.Payment;
import com.grocery.payment.exception.ExternalServiceException;
import com.grocery.payment.gateway.PaymentGateway;
import com.grocery.payment.gateway.razorpay.dto.RazorpayOrder;
import com.grocery.payment.gateway.razorpay.dto.RazorpayPaymentList;
import com.grocery.payment.gateway.razorpay.dto.RazorpayRefund;
import com.grocery.payment.logging.Loggable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Razorpay through its REST API (https://razorpay.com/docs/api/). Three calls:
 *
 *   POST /orders                       open an order the customer pays in Razorpay Checkout
 *   POST /payments/{paymentId}/refund  give the money back
 *   GET  /orders/{orderId}/payments    was the order paid? (checked before a payment times out)
 *
 * Amounts are sent in paise (the smallest unit of INR): 158.00 rupees = 15800.
 * Any failure (Razorpay down, timeout, 4xx/5xx) becomes ExternalServiceException.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "razorpay")
@Loggable   // Calls Razorpay over the internet: LoggingAspect logs the time of every call and warns when it is slow
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final String CAPTURED = "captured";

    private final RestClient razorpayRestClient;

    public RazorpayPaymentGateway(RestClient razorpayRestClient) {
        this.razorpayRestClient = razorpayRestClient;
    }

    @Override
    public String createGatewayOrder(Payment payment) {
        Map<String, Object> body = Map.of(
                "amount", toPaise(payment.getAmount()),
                "currency", "INR",
                "receipt", "order_" + payment.getOrderId(),
                "notes", Map.of("orderId", String.valueOf(payment.getOrderId())));
        RazorpayOrder order = call("open a payment", () -> razorpayRestClient.post()
                .uri("/orders")
                .body(body)
                .retrieve()
                .body(RazorpayOrder.class));
        log.info("[razorpay] opened {} for {} (order id={})", order.id(), payment.getAmount(), payment.getOrderId());
        return order.id();
    }

    @Override
    public String refund(Payment payment) {
        RazorpayRefund refund = call("refund the payment", () -> razorpayRestClient.post()
                .uri("/payments/{paymentId}/refund", payment.getGatewayPaymentId())
                .body(Map.of("amount", toPaise(payment.getAmount())))
                .retrieve()
                .body(RazorpayRefund.class));
        log.info("[razorpay] refunded {} of {} (order id={}): {}", payment.getAmount(), payment.getGatewayPaymentId(),
                payment.getOrderId(), refund.id());
        return refund.id();
    }

    @Override
    public Optional<String> findCapturedPayment(String gatewayOrderId) {
        RazorpayPaymentList payments = call("read the payments of an order", () -> razorpayRestClient.get()
                .uri("/orders/{orderId}/payments", gatewayOrderId)
                .retrieve()
                .body(RazorpayPaymentList.class));
        return payments.items().stream()
                .filter(item -> CAPTURED.equals(item.status()))
                .map(RazorpayPaymentList.Item::id)
                .findFirst();
    }

    /** 158.00 -> 15800 */
    static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).longValueExact();
    }

    private <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Razorpay could not " + action + ". Please try again later.", ex);
        }
    }
}
