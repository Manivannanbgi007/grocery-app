package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CustomerClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.AddressData;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.CheckoutData;
import com.grocery.mcpserver.tool.result.OrderPreview;
import com.grocery.mcpserver.tool.result.UnpaidOrder;
import com.grocery.mcpserver.client.dto.OrderData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.config.McpTransportConfig;
import com.grocery.mcpserver.tool.ToolResult.Status;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** prepareMyOrder only reads and summarises: the order is placed by the customer's click, never by the model. */
@ExtendWith(MockitoExtension.class)
class OrderPrepareToolsTest {

    private static final McpTransportContext LOGGED_IN = McpTransportContext.create(Map.of(McpTransportConfig.USER_TOKEN, "customer-jwt"));
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final AddressData HOME = new AddressData(3L, "HOME", "Priya Sharma", "12, 5th Cross", "Indiranagar", null,
            "Bengaluru", "560038", true);
    private static final AddressData WORK = new AddressData(5L, "WORK", "Priya Sharma", "4th Floor, Prestige Tower", null, null,
            "Bengaluru", "560001", false);

    @Mock
    private CustomerClient customers;
    @Mock
    private ShopInfoClient shopInfo;

    private OrderPrepareTools tools;

    @BeforeEach
    void setUp() {
        tools = new OrderPrepareTools(customers, shopInfo, new ToolRunner(), Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(shopInfo.stores()).thenReturn(TestData.stores());
        lenient().when(customers.addresses("customer-jwt")).thenReturn(List.of(WORK, HOME));
        lenient().when(customers.latestOrders("customer-jwt", 1)).thenReturn(new PageData<>(List.of(), 0));
    }

    @Test
    void theSummaryHasTheCartsAmountsTheAddressAndThePayment_andOrdersNothing() {
        when(customers.checkout("customer-jwt")).thenReturn(readyCheckout());

        var result = tools.prepareMyOrder("cash", null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).startsWith("Order summary prepared; NOTHING is ordered yet.");
        var preview = preview(result);
        assertThat(preview.store()).isEqualTo("Fresh Mart");
        assertThat(preview.items()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Tomato");
            assertThat(line.lineTotal()).isEqualTo("₹72");
        });
        assertThat(preview.deliveryFee()).isEqualTo("₹30");
        assertThat(preview.payable()).isEqualTo("₹102");
        assertThat(preview.deliverTo()).isEqualTo("Home: Priya Sharma, 12, 5th Cross, Indiranagar, Bengaluru 560038");
        assertThat(preview.payment()).isEqualTo("Cash on delivery");
        assertThat(preview.recentOrder()).isNull();
        assertThat(preview.confirm().addressId()).isEqualTo(3L);
        assertThat(preview.confirm().paymentMethod()).isEqualTo("COD");
        assertThat(preview.confirm().checkoutFingerprint()).isEqualTo("fp-ready-cart-001");
    }

    @Test
    void aNamedAddressIsUsed_andAnUnknownOneIsSaid() {
        when(customers.checkout("customer-jwt")).thenReturn(readyCheckout());

        assertThat(preview(tools.prepareMyOrder("online", "office", LOGGED_IN)).confirm().addressId()).isEqualTo(5L);
        assertThat(preview(tools.prepareMyOrder("UPI", "Indiranagar", LOGGED_IN)).deliverTo()).startsWith("Home:");

        var unknown = tools.prepareMyOrder("cash", "beach house", LOGGED_IN);
        assertThat(unknown.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(unknown.message()).startsWith("The customer has no address that matches \"beach house\". Their addresses: Work:");
    }

    @Test
    void withoutAPaymentMethodTheCustomerIsAsked() {
        when(customers.checkout("customer-jwt")).thenReturn(readyCheckout());

        var noMethod = tools.prepareMyOrder(null, null, LOGGED_IN);
        assertThat(noMethod.status()).isEqualTo(Status.CHOOSE);
        assertThat(noMethod.message()).isEqualTo("Ask the customer how they want to pay: cash on delivery, or online (UPI or card) "
                + "right after confirming.");
        assertThat(tools.prepareMyOrder("bitcoin", null, LOGGED_IN).status()).isEqualTo(Status.CHOOSE);
    }

    @Test
    void aCartThatCannotBeOrderedYetIsSaidWithItsProblemsInRupees() {
        when(customers.checkout("customer-jwt")).thenReturn(new CheckoutData(1L, List.of(), null, totals("0", "0"), false,
                List.of("Tomato is out of stock", "The minimum order value is 99.00 (add 59.00 more)"), "fp-x"));

        var result = tools.prepareMyOrder("cash", null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.REFUSED);
        assertThat(result.message()).isEqualTo("The cart cannot be ordered yet: Tomato is out of stock; The minimum order value "
                + "is ₹99 (add ₹59 more).");
    }

    @Test
    void withoutAnAddressTheCustomerIsSentToTheAddressesPage() {
        when(customers.checkout("customer-jwt")).thenReturn(readyCheckout());
        when(customers.addresses("customer-jwt")).thenReturn(List.of());

        var result = tools.prepareMyOrder("cash", null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.message()).contains("no delivery address", "Addresses page");
    }

    @Test
    void anOrderPlacedAMomentAgoIsPointedOut() {
        when(customers.checkout("customer-jwt")).thenReturn(readyCheckout());
        when(customers.latestOrders("customer-jwt", 1)).thenReturn(new PageData<>(List.of(
                new OrderSummaryData(196, "Fresh Mart", "PENDING", 2, new BigDecimal("102.00"), NOW.minusSeconds(180))), 1));

        assertThat(preview(tools.prepareMyOrder("cash", null, LOGGED_IN)).recentOrder()).isEqualTo("Order #196 was placed 3 minutes ago.");
    }

    @Test
    void payingAgainAfterTheOrderWasPlaced_ordersNothingNew_andGivesTheOrdersOwnPaymentLink() {
        // "Proceed to pay" after Confirm: the cart is empty, order #207 (online) waits for its payment
        when(customers.checkout("customer-jwt")).thenReturn(new CheckoutData(null, List.of(), null, totals("0", "0"), false,
                List.of("Your cart is empty"), null));
        when(customers.latestOrders("customer-jwt", 1)).thenReturn(new PageData<>(List.of(
                new OrderSummaryData(207, "Fresh Mart", "PENDING", 1, new BigDecimal("138.00"), NOW.minusSeconds(60))), 1));
        when(customers.order("customer-jwt", 207L)).thenReturn(Optional.of(order(207, "PENDING", "ONLINE", "PENDING", "/orders/207")));

        var result = tools.prepareMyOrder("online", null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.REFUSED);
        assertThat(result.message()).isEqualTo("Nothing new to order: order #207 (₹138) is placed and waits for the customer's "
                + "online payment: they pay it with the Pay Now button.");
        assertThat(result.data()).isEqualTo(new UnpaidOrder(207, "₹138", "/orders/207"));
    }

    @Test
    void payingAgainRightAfterTheOrder_whileTheCartStillHoldsItsProducts_isThatOrderToo() {
        // live: order-service empties the cart a moment after the order; "Proceed to pay" came first and got a 2nd summary
        when(customers.checkout("customer-jwt")).thenReturn(checkoutOf(3));
        when(customers.latestOrders("customer-jwt", 1)).thenReturn(new PageData<>(List.of(
                new OrderSummaryData(207, "Fresh Mart", "PENDING", 1, new BigDecimal("138.00"), NOW.minusSeconds(5))), 1));
        when(customers.order("customer-jwt", 207L)).thenReturn(Optional.of(order(207, "PENDING", "ONLINE", "PENDING", "/orders/207")));

        var result = tools.prepareMyOrder("online", null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.REFUSED);
        assertThat(result.data()).isEqualTo(new UnpaidOrder(207, "₹138", "/orders/207"));
    }

    @Test
    void aNewCartWhileAnOrderWaitsForItsPayment_isANewOrder() {
        // 5 kg of tomatoes now, the unpaid order had 3: the customer orders something new
        when(customers.checkout("customer-jwt")).thenReturn(checkoutOf(5));
        when(customers.latestOrders("customer-jwt", 1)).thenReturn(new PageData<>(List.of(
                new OrderSummaryData(207, "Fresh Mart", "PENDING", 1, new BigDecimal("138.00"), NOW.minusSeconds(60))), 1));
        when(customers.order("customer-jwt", 207L)).thenReturn(Optional.of(order(207, "PENDING", "ONLINE", "PENDING", "/orders/207")));

        var result = tools.prepareMyOrder("online", null, LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(preview(result).recentOrder()).isEqualTo("Order #207 was placed 1 minute ago.");
    }

    @Test
    void anEmptyCartAfterAPaidOrIsCashOrder_isJustAnEmptyCart() {
        when(customers.checkout("customer-jwt")).thenReturn(new CheckoutData(null, List.of(), null, totals("0", "0"), false,
                List.of("Your cart is empty"), null));
        when(customers.latestOrders("customer-jwt", 1)).thenReturn(new PageData<>(List.of(
                new OrderSummaryData(207, "Fresh Mart", "PENDING", 1, new BigDecimal("138.00"), NOW.minusSeconds(60))), 1));
        when(customers.order("customer-jwt", 207L)).thenReturn(Optional.of(order(207, "CONFIRMED", "ONLINE", "PAID", null)));

        var result = tools.prepareMyOrder("online", null, LOGGED_IN);

        assertThat(result.message()).isEqualTo("The cart cannot be ordered yet: Your cart is empty.");
        assertThat(result.data()).isNull();
    }

    @Test
    void withoutALoginNothingIsRead() {
        assertThat(tools.prepareMyOrder("cash", null, McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        verifyNoInteractions(customers);
    }

    @Test
    void theCustomersWordsForThePayment() {
        assertThat(OrderPrepareTools.paymentMethodOf("Cash on delivery")).isEqualTo("COD");
        assertThat(OrderPrepareTools.paymentMethodOf("cod")).isEqualTo("COD");
        assertThat(OrderPrepareTools.paymentMethodOf("pay online")).isEqualTo("ONLINE");
        assertThat(OrderPrepareTools.paymentMethodOf("UPI")).isEqualTo("ONLINE");
        assertThat(OrderPrepareTools.paymentMethodOf("credit card")).isEqualTo("ONLINE");
        assertThat(OrderPrepareTools.paymentMethodOf("later")).isNull();
        assertThat(OrderPrepareTools.paymentMethodOf(null)).isNull();
    }

    @Test
    void aCancellationSummaryCancelsNothing_andSaysWhatHappensToTheMoney() {
        when(customers.order("customer-jwt", 198L)).thenReturn(Optional.of(order(198, "PACKED", "COD", "CASH_ON_DELIVERY", true)));
        when(customers.order("customer-jwt", 199L)).thenReturn(Optional.of(order(199, "CONFIRMED", "ONLINE", "PAID", true)));

        var cash = tools.prepareMyOrderCancellation(198L, " Ordered by mistake ", LOGGED_IN);
        var online = tools.prepareMyOrderCancellation(199L, null, LOGGED_IN);

        assertThat(cash.status()).isEqualTo(Status.OK);
        assertThat(cash.message()).startsWith("Cancellation summary prepared; NOTHING is cancelled yet.");
        assertThat(cash.data().orderNumber()).isEqualTo(198L);
        assertThat(cash.data().status()).isEqualTo("Packed by the store");
        assertThat(cash.data().total()).isEqualTo("₹138");
        assertThat(cash.data().items()).singleElement().satisfies(line -> assertThat(line.name()).isEqualTo("Tomato"));
        assertThat(cash.data().refund()).isEqualTo("Nothing was paid yet (cash on delivery).");
        assertThat(cash.data().confirm().orderId()).isEqualTo(198L);
        assertThat(cash.data().confirm().reason()).isEqualTo("Ordered by mistake");
        assertThat(online.data().refund()).isEqualTo("The online payment of ₹138 will be refunded.");
        // order-service takes at most 255 characters
        assertThat(tools.prepareMyOrderCancellation(198L, "x".repeat(300), LOGGED_IN).data().confirm().reason()).hasSize(250);
    }

    @Test
    void anOrderThatCanNoLongerBeCancelledOrIsNotTheirsIsSaid() {
        when(customers.order("customer-jwt", 36L)).thenReturn(Optional.of(order(36, "DELIVERED", "COD", "PAID", false)));
        when(customers.order("customer-jwt", 35L)).thenReturn(Optional.of(order(35, "CANCELLED", "COD", "CANCELLED", false)));
        when(customers.order("customer-jwt", 6L)).thenReturn(Optional.empty());      // order-service: 404 for another customer's order

        assertThat(tools.prepareMyOrderCancellation(36L, null, LOGGED_IN).message())
                .isEqualTo("Order #36 can no longer be cancelled: it is delivered.");
        assertThat(tools.prepareMyOrderCancellation(35L, null, LOGGED_IN).message()).isEqualTo("Order #35 was cancelled earlier, so there is nothing to cancel.");
        var notTheirs = tools.prepareMyOrderCancellation(6L, null, LOGGED_IN);
        assertThat(notTheirs.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(notTheirs.message()).isEqualTo("The customer has no order with the number 6.");
        assertThat(tools.prepareMyOrderCancellation(0L, null, LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
    }

    @Test
    void withoutANumberTheOnlyOrderThatCanBeCancelledIsTaken_andSeveralAreAskedAbout() {
        when(customers.latestOrders("customer-jwt", OrderPrepareTools.CANCEL_CANDIDATES)).thenReturn(new PageData<>(List.of(
                summary(199), summary(198), summary(36)), 3));
        when(customers.order("customer-jwt", 199L)).thenReturn(Optional.of(order(199, "PACKED", "COD", "CASH_ON_DELIVERY", true)));
        when(customers.order("customer-jwt", 198L)).thenReturn(Optional.of(order(198, "CANCELLED", "COD", "CANCELLED", false)));
        when(customers.order("customer-jwt", 36L)).thenReturn(Optional.of(order(36, "DELIVERED", "COD", "PAID", false)));

        assertThat(tools.prepareMyOrderCancellation(null, null, LOGGED_IN).data().orderNumber()).isEqualTo(199L);

        when(customers.order("customer-jwt", 198L)).thenReturn(Optional.of(order(198, "CONFIRMED", "ONLINE", "PAID", true)));
        var several = tools.prepareMyOrderCancellation(null, null, LOGGED_IN);
        assertThat(several.status()).isEqualTo(Status.CHOOSE);
        assertThat(several.message()).isEqualTo("Several orders can still be cancelled: #199 (₹138), #198 (₹138). Ask the customer which one.");
    }

    /** prepareMyOrder's summary (its data is typed Object: a summary, or the order that waits for its payment) */
    private static OrderPreview preview(ToolResult<Object> result) {
        return (OrderPreview) result.data();
    }

    private static OrderData order(long id, String status, String paymentMethod, String paymentStatus, String paymentUrl) {
        return new OrderData(id, "Fresh Mart", status, null, paymentMethod, paymentStatus, paymentUrl,
                List.of(new OrderData.Item("Tomato", "KG", BigDecimal.ONE, new BigDecimal("36.00"), 3, new BigDecimal("108.00"))),
                new OrderData.Amounts(new BigDecimal("108.00"), null, BigDecimal.ZERO, new BigDecimal("30.00"), new BigDecimal("138.00")),
                false, null, List.of(), NOW.minusSeconds(60), null);
    }

    private static OrderData order(long id, String status, String paymentMethod, String paymentStatus, boolean cancellable) {
        return new OrderData(id, "Fresh Mart", status, null, paymentMethod, paymentStatus, null,
                List.of(new OrderData.Item("Tomato", "KG", BigDecimal.ONE, new BigDecimal("36.00"), 3, new BigDecimal("108.00"))),
                new OrderData.Amounts(new BigDecimal("108.00"), null, BigDecimal.ZERO, new BigDecimal("30.00"), new BigDecimal("138.00")),
                cancellable, null, List.of(), NOW.minusSeconds(3600), null);
    }

    private static OrderSummaryData summary(long id) {
        return new OrderSummaryData(id, "Fresh Mart", "PACKED", 1, new BigDecimal("138.00"), NOW.minusSeconds(3600));
    }

    /** 2 x Tomato (₹72) + ₹30 delivery = ₹102 */
    private static CheckoutData readyCheckout() {
        return checkoutOf(2);
    }

    /** A cart that can be ordered: this many packs of Tomato (1 kg) */
    private static CheckoutData checkoutOf(int tomatoes) {
        CartData cart = TestData.cartWith(TestData.tomato(), tomatoes);
        return new CheckoutData(1L, cart.items(), null, cart.totals(), true, List.of(), "fp-ready-cart-001");
    }

    private static CartData.Totals totals(String items, String payable) {
        return new CartData.Totals(new BigDecimal(items), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(payable),
                new BigDecimal("99"), false);
    }
}
