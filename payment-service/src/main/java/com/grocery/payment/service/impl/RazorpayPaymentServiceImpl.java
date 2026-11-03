package com.grocery.payment.service.impl;

import com.grocery.payment.config.PaymentProperties;
import com.grocery.payment.dto.request.RazorpayVerifyRequest;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.dto.response.RazorpayCheckoutResponse;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.BusinessException;
import com.grocery.payment.exception.ResourceNotFoundException;
import com.grocery.payment.gateway.razorpay.RazorpaySignatureVerifier;
import com.grocery.payment.gateway.razorpay.dto.RazorpayWebhookEvent;
import com.grocery.payment.mapper.PaymentMapper;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.service.OnlinePaymentRecorder;
import com.grocery.payment.service.ProcessedEventService;
import com.grocery.payment.service.RazorpayPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "razorpay")
public class RazorpayPaymentServiceImpl implements RazorpayPaymentService {

    /** Every payment is in Indian rupees */
    private static final String CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OnlinePaymentRecorder onlinePaymentRecorder;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final ProcessedEventService processedEventService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public RazorpayCheckoutResponse getCheckout(Long customerUserId, Long orderId) {
        Payment payment = findMyPayment(customerUserId, orderId);
        payment.checkCanBePaidNow();
        return new RazorpayCheckoutResponse(
                paymentProperties.razorpay().keyId(),
                payment.getGatewayOrderId(),
                inPaise(payment),
                CURRENCY,
                "Grocery order #" + orderId,
                payment.getPayBefore());
    }

    @Override
    @Transactional
    public PaymentResponse verifyCheckout(Long customerUserId, Long orderId, RazorpayVerifyRequest request) {
        Payment payment = findMyPayment(customerUserId, orderId);
        boolean valid = request.razorpayOrderId().equals(payment.getGatewayOrderId())
                && signatureVerifier.isValidCheckoutSignature(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature());
        if (!valid) {
            log.warn("Order id={}: checkout signature does not match", orderId);
            throw new BusinessException("The payment could not be verified");
        }
        onCaptured(payment, request.razorpayPaymentId());
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public String handleWebhook(String rawBody, String signature, String eventId) {
        if (!signatureVerifier.isValidWebhookSignature(rawBody, signature)) {
            log.warn("Razorpay webhook with a wrong signature - rejected");
            throw new BusinessException("Invalid webhook signature");
        }
        RazorpayWebhookEvent webhook = parse(rawBody);
        RazorpayWebhookEvent.PaymentEntity gatewayPayment = webhook.payment();
        if (gatewayPayment == null || gatewayPayment.orderId() == null) {
            return "Ignored: " + webhook.event() + " is not about an order payment";
        }
        String duplicateKey = eventId != null ? eventId : webhook.event() + ":" + gatewayPayment.id();
        if (!processedEventService.markAsProcessed(duplicateKey, "razorpay." + webhook.event())) {
            return "Ignored: already handled";
        }
        Optional<Payment> found = paymentRepository.findByGatewayOrderId(gatewayPayment.orderId());
        if (found.isEmpty()) {
            log.warn("Razorpay {} for unknown order {} - ignored", webhook.event(), gatewayPayment.orderId());
            return "Ignored: unknown order";
        }
        Payment payment = found.get();
        // Razorpay takes only the order's amount, so this never happens unless something is badly wrong: then a person
        // looks at it in the Razorpay dashboard, and the order is not marked paid
        if (RazorpayWebhookEvent.PAYMENT_CAPTURED.equals(webhook.event()) && !paysTheOrderAmount(payment, gatewayPayment)) {
            log.error("Razorpay payment {} for order id={} is {} (in paise) in {}, but the order is {} in {} - not marked paid",
                    gatewayPayment.id(), payment.getOrderId(), gatewayPayment.amount(), gatewayPayment.currency(),
                    inPaise(payment), CURRENCY);
            return "Ignored: the amount or currency does not match the order";
        }

        switch (webhook.event()) {
            case RazorpayWebhookEvent.PAYMENT_CAPTURED -> onCaptured(payment, gatewayPayment.id());
            case RazorpayWebhookEvent.PAYMENT_FAILED -> onFailedAttempt(payment, gatewayPayment.errorDescription());
            default -> log.debug("Razorpay {} for order id={} - nothing to do", webhook.event(), payment.getOrderId());
        }
        return "Processed: " + webhook.event();
    }

    /** Whether Razorpay's payment is exactly the order's amount, in rupees */
    private static boolean paysTheOrderAmount(Payment payment, RazorpayWebhookEvent.PaymentEntity gatewayPayment) {
        return CURRENCY.equals(gatewayPayment.currency())
                && gatewayPayment.amount() != null && gatewayPayment.amount() == inPaise(payment);
    }

    /** Razorpay counts in paise, the smallest unit of INR: 158.00 rupees = 15800 */
    private static long inPaise(Payment payment) {
        return payment.getAmount().movePointRight(2).longValueExact();
    }

    /** The money arrived (Checkout callback or webhook - whichever comes first does the work). */
    private void onCaptured(Payment payment, String gatewayPaymentId) {
        switch (payment.getStatus()) {
            case AWAITING_PAYMENT -> onlinePaymentRecorder.paid(payment, gatewayPaymentId);
            case FAILED, CANCELLED -> onlinePaymentRecorder.refundLatePayment(payment, gatewayPaymentId);
            default -> log.info("Order id={}: payment is already {} - nothing to do", payment.getOrderId(), payment.getStatus());
        }
    }

    /** The customer can try again until the deadline, so only the reason is kept. */
    private void onFailedAttempt(Payment payment, String reason) {
        if (payment.getStatus() == PaymentStatus.AWAITING_PAYMENT) {
            payment.recordFailedAttempt(reason == null ? "The payment attempt failed" : reason);
            log.info("Order id={}: a payment attempt failed ({}); the customer can try again", payment.getOrderId(), reason);
        }
    }

    private RazorpayWebhookEvent parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, RazorpayWebhookEvent.class);
        } catch (JacksonException ex) {
            throw new BusinessException("The webhook body is not valid JSON");
        }
    }

    /** Another customer's payment looks like it does not exist. */
    private Payment findMyPayment(Long customerUserId, Long orderId) {
        return paymentRepository.findByOrderIdAndCustomerUserId(orderId, customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order " + orderId
                        + ". If you have just placed it, try again in a few seconds."));
    }
}
