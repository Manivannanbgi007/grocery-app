package com.grocery.order.support;

import com.grocery.order.client.dto.CheckoutSummary;
import com.grocery.order.client.dto.CustomerAddress;
import com.grocery.order.client.dto.StoreInfo;
import com.grocery.order.entity.DeliveryAddress;
import com.grocery.order.entity.Order;
import com.grocery.order.entity.OrderAmounts;
import com.grocery.order.entity.OrderItem;
import com.grocery.order.entity.StoreDetails;
import com.grocery.order.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builders for test data, matching the seed data (customer1 = user 4, Fresh Mart = store 1).
 */
public final class TestOrders {

    private TestOrders() {
    }

    /** Tomato x2 (72.00) + Amul Butter x1 (56.00) = 128.00 + 30.00 delivery = 158.00 */
    public static CheckoutSummary readyCheckout() {
        return new CheckoutSummary(4L, 1L,
                List.of(checkoutItem(1L, "Tomato", "40.00", "36.00", 2, "72.00"),
                        checkoutItem(7L, "Amul Butter", "56.00", "56.00", 1, "56.00")),
                null,
                new CheckoutSummary.Totals(new BigDecimal("128.00"), BigDecimal.ZERO, new BigDecimal("30.00"),
                        new BigDecimal("158.00"), new BigDecimal("6.00")),
                true, List.of(), "fp-ready-cart-001");
    }

    public static CheckoutSummary notReadyCheckout(String... problems) {
        return new CheckoutSummary(4L, 1L, List.of(), null,
                new CheckoutSummary.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                false, List.of(problems), "fp-not-ready-001");
    }

    public static CustomerAddress homeAddress(String pincode) {
        return new CustomerAddress(1L, "Priya Sharma", "9000000004", "12, 5th Cross", null, "Near Metro",
                "Bengaluru", "Karnataka", pincode, null, null);
    }

    public static StoreInfo freshMart(boolean openNow) {
        return new StoreInfo(1L, "Fresh Mart", "9000000003", "45, 100 Feet Road", "Indiranagar", "Bengaluru", "560038",
                "ACTIVE", true, openNow, List.of("560038", "560008"), new BigDecimal("10.00"),
                new BigDecimal("12.978400"), new BigDecimal("77.640800"));
    }

    /** A PENDING order of customer 4 at store 1. */
    public static Order pendingOrder(PaymentMethod paymentMethod) {
        OrderItem tomato = new OrderItem();
        tomato.setProductId(1L);
        tomato.setProductName("Tomato");
        tomato.setUnit("KG");
        tomato.setUnitQuantity(BigDecimal.ONE);
        tomato.setPrice(new BigDecimal("40.00"));
        tomato.setUnitPrice(new BigDecimal("36.00"));
        tomato.setTaxPercentage(BigDecimal.ZERO);
        tomato.setQuantity(2);
        tomato.setLineTotal(new BigDecimal("72.00"));
        OrderItem butter = new OrderItem();
        butter.setProductId(7L);
        butter.setProductName("Amul Butter");
        butter.setUnit("G");
        butter.setUnitQuantity(new BigDecimal("100"));
        butter.setPrice(new BigDecimal("56.00"));
        butter.setUnitPrice(new BigDecimal("56.00"));
        butter.setTaxPercentage(new BigDecimal("12.00"));
        butter.setQuantity(1);
        butter.setLineTotal(new BigDecimal("56.00"));

        return Order.place(4L, 1L,
                new StoreDetails("Fresh Mart", "9000000003", "45, 100 Feet Road, Indiranagar, Bengaluru 560038", "560038",
                        new BigDecimal("10.00"), new BigDecimal("12.978400"), new BigDecimal("77.640800")),
                new DeliveryAddress("Priya Sharma", "9000000004", "12, 5th Cross", null, null, "Bengaluru", "Karnataka",
                        "560038", new BigDecimal("12.971900"), new BigDecimal("77.641200")),
                paymentMethod,
                new OrderAmounts(new BigDecimal("128.00"), null, BigDecimal.ZERO, new BigDecimal("30.00"),
                        new BigDecimal("6.00"), new BigDecimal("158.00")),
                null,
                List.of(tomato, butter));
    }

    private static CheckoutSummary.Item checkoutItem(Long productId, String name, String price, String sellingPrice,
                                                     int quantity, String lineTotal) {
        return new CheckoutSummary.Item(productId, name, "KG", BigDecimal.ONE, null, new BigDecimal(price),
                new BigDecimal(sellingPrice), BigDecimal.ZERO, quantity, new BigDecimal(lineTotal));
    }
}
