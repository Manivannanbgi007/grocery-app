package com.grocery.payment.gateway;

import com.grocery.payment.entity.Payment;

import java.util.Optional;

/**
 * The outside payment provider. The rest of the service only talks to this interface, so the provider
 * can be swapped by configuration (app.payment.gateway):
 *
 *   mock      MockPaymentGateway - no real money, used locally and in tests
 *   razorpay  RazorpayPaymentGateway - Razorpay's REST API (test mode or live)
 */
public interface PaymentGateway {

    /** Opens an online payment the customer can pay; returns the gateway's order id. */
    String createGatewayOrder(Payment payment);

    /** Gives the money of a paid payment back; returns the gateway's refund id. */
    String refund(Payment payment);

    /**
     * The successful payment of a gateway order, if the customer paid. Asked before an unpaid payment is
     * failed at its deadline, in case the gateway's message about the payment never reached us.
     */
    Optional<String> findCapturedPayment(String gatewayOrderId);
}
