package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.AddressData;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.CheckoutData;
import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.client.dto.OrderData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * The logged-in customer's own data through the API gateway: the cart and its checkout (cart-service), the orders
 * (order-service) and the delivery addresses (customer-service).
 *
 * Every call carries the customer's token (Authorization: Bearer ...). The gateway checks it and tells the services
 * who the customer is, so these calls can only return that customer's own data, exactly like the website.
 * Nothing is cached: carts and orders change all the time, and every customer has their own.
 */
@Component
@RequiredArgsConstructor
public class CustomerClient {

    private static final ParameterizedTypeReference<GatewayResponse<CartData>> CART = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<OrderSummaryData>>> ORDER_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<OrderData>> ORDER = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<CheckoutData>> CHECKOUT = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<List<AddressData>>> ADDRESSES = new ParameterizedTypeReference<>() { };

    private final RestClient gatewayRestClient;

    /** GET /api/v1/cart */
    public CartData cart(String customerToken) {
        return GatewayCall.dataOf("The cart", () -> gatewayRestClient.get()
                .uri("/api/v1/cart")
                .headers(headers -> headers.setBearerAuth(customerToken))
                .retrieve()
                .body(CART));
    }

    /** GET /api/v1/cart/checkout: the cart checked against today's catalog, as an order of it would be now. */
    public CheckoutData checkout(String customerToken) {
        return GatewayCall.dataOf("The cart", () -> gatewayRestClient.get()
                .uri("/api/v1/cart/checkout")
                .headers(headers -> headers.setBearerAuth(customerToken))
                .retrieve()
                .body(CHECKOUT));
    }

    /** GET /api/v1/customers/me/addresses: the customer's delivery addresses. */
    public List<AddressData> addresses(String customerToken) {
        return GatewayCall.dataOf("The address list", () -> gatewayRestClient.get()
                .uri("/api/v1/customers/me/addresses")
                .headers(headers -> headers.setBearerAuth(customerToken))
                .retrieve()
                .body(ADDRESSES));
    }

    /** GET /api/v1/orders: the newest orders first. */
    public PageData<OrderSummaryData> latestOrders(String customerToken, int size) {
        return GatewayCall.dataOf("The order list", () -> gatewayRestClient.get()
                .uri("/api/v1/orders?page=0&size={size}", size)
                .headers(headers -> headers.setBearerAuth(customerToken))
                .retrieve()
                .body(ORDER_PAGE));
    }

    /** GET /api/v1/orders/{orderId}; empty when the customer has no order with this number. */
    public Optional<OrderData> order(String customerToken, long orderId) {
        return GatewayCall.optionalDataOf("The order", () -> gatewayRestClient.get()
                .uri("/api/v1/orders/{orderId}", orderId)
                .headers(headers -> headers.setBearerAuth(customerToken))
                .retrieve()
                .body(ORDER));
    }
}
