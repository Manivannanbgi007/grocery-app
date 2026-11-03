package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CustomerClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.OrderData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.config.McpTransportConfig;
import com.grocery.mcpserver.exception.LoginRequiredException;
import com.grocery.mcpserver.tool.ToolResult.Status;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerToolsTest {

    private static final McpTransportContext LOGGED_IN = McpTransportContext.create(Map.of(McpTransportConfig.USER_TOKEN, "customer-jwt"));

    @Mock
    private CustomerClient customers;
    @Mock
    private ShopInfoClient shopInfo;

    private CustomerTools tools;

    @BeforeEach
    void setUp() {
        tools = new CustomerTools(customers, shopInfo, new ToolRunner());
        lenient().when(shopInfo.stores()).thenReturn(TestData.stores());
    }

    @Test
    void withoutALoginTheToolsAskTheCustomerToLogIn() {
        assertThat(tools.getMyCart(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getMyOrders(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getMyOrderDetails(36L, McpTransportContext.EMPTY).message())
                .isEqualTo("The customer is not logged in. Ask them to log in on the website to see their cart and orders.");
        verifyNoInteractions(customers);
    }

    @Test
    void theCartShowsTheLinesAndTheTotalsOfCartService() {
        when(customers.cart("customer-jwt")).thenReturn(new CartData(1L,
                List.of(new CartData.Item(7L, "Amul Butter", "G", new BigDecimal("100"), new BigDecimal("56.00"), 2, new BigDecimal("112.00"))),
                "SAVE10", "Coupon SAVE10 applied", new CartData.Totals(new BigDecimal("112.00"), BigDecimal.ZERO,
                new BigDecimal("11.20"), new BigDecimal("30.00"), new BigDecimal("130.80"), new BigDecimal("99"), true)));

        var result = tools.getMyCart(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The cart has 1 product.");
        assertThat(result.data().store()).isEqualTo("Fresh Mart");
        assertThat(result.data().items()).singleElement().satisfies(line -> {
            assertThat(line.packSize()).isEqualTo("100 g");
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.unitPrice()).isEqualTo("₹56");
            assertThat(line.lineTotal()).isEqualTo("₹112");
        });
        assertThat(result.data().couponDiscount()).isEqualTo("₹11.20");
        assertThat(result.data().deliveryFee()).isEqualTo("₹30");
        assertThat(result.data().payable()).isEqualTo("₹130.80");
        assertThat(result.data().savings()).isNull();
        // also when it is reached: the model must not repeat an earlier "does not reach it yet" from memory
        assertThat(result.data().minimumOrder()).isEqualTo("The minimum order is ₹99; this cart reaches it.");
    }

    @Test
    void anEmptyCartIsSaidPlainly() {
        when(customers.cart("customer-jwt")).thenReturn(new CartData(null, List.of(), null, null,
                new CartData.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("99"), false)));

        var result = tools.getMyCart(LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("The cart is empty.");
        assertThat(result.data()).isNull();
    }

    @Test
    void theOrdersAreListedWithReadableStatuses() {
        when(customers.latestOrders("customer-jwt", CustomerTools.LATEST_ORDERS)).thenReturn(new PageData<>(List.of(
                new OrderSummaryData(36, "Fresh Mart", "OUT_FOR_DELIVERY", 3, new BigDecimal("138.00"), Instant.parse("2026-10-26T14:57:33Z"))), 33));

        var result = tools.getMyOrders(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The customer has 33 orders; these are the 1 newest.");
        assertThat(result.data()).singleElement().satisfies(order -> {
            assertThat(order.orderNumber()).isEqualTo(36);
            assertThat(order.status()).isEqualTo("On the way");
            assertThat(order.total()).isEqualTo("₹138");
            assertThat(order.placed()).isEqualTo("26 Oct 2026, 8:27 pm");
        });
    }

    @Test
    void orderDetailsShowTheLatestStepAndThePayment() {
        when(customers.order("customer-jwt", 36L)).thenReturn(Optional.of(new OrderData(36, "Fresh Mart", "PACKED", null,
                "ONLINE", "PAID", null,
                List.of(new OrderData.Item("Tomato", "KG", BigDecimal.ONE, new BigDecimal("36.00"), 3, new BigDecimal("108.00"))),
                new OrderData.Amounts(new BigDecimal("108.00"), null, BigDecimal.ZERO, new BigDecimal("30.00"), new BigDecimal("138.00")),
                true, null,
                List.of(new OrderData.Step("PENDING", "Order placed", Instant.parse("2026-10-26T14:57:33Z")),
                        new OrderData.Step("PACKED", "Your items are packed", Instant.parse("2026-10-26T15:10:00Z"))),
                Instant.parse("2026-10-26T14:57:33Z"), null)));

        var result = tools.getMyOrderDetails(36L, LOGGED_IN);

        assertThat(result.data().status()).isEqualTo("Packed by the store");
        assertThat(result.data().latestUpdate()).isEqualTo("Your items are packed (26 Oct 2026, 8:40 pm)");
        assertThat(result.data().payment()).isEqualTo("Online payment, paid");
        assertThat(result.data().cancelling()).isEqualTo("Can still be cancelled: ask here in the chat, or use My orders on the website");
        assertThat(result.data().total()).isEqualTo("₹138");
        assertThat(result.data().items()).singleElement().satisfies(line -> {
            assertThat(line.unitPrice()).isEqualTo("₹36");
            assertThat(line.lineTotal()).isEqualTo("₹108");
        });
    }

    @Test
    void unknownOrBadOrderNumbers() {
        when(customers.order("customer-jwt", 99L)).thenReturn(Optional.empty());

        assertThat(tools.getMyOrderDetails(99L, LOGGED_IN).message()).isEqualTo("The customer has no order with the number 99.");
        assertThat(tools.getMyOrderDetails(0L, LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(tools.getMyOrderDetails(null, LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
    }

    @Test
    void anExpiredLoginGivesLoginRequired() {
        when(customers.cart("customer-jwt")).thenThrow(new LoginRequiredException("The customer's login has expired."));

        var result = tools.getMyCart(LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(result.message()).isEqualTo("The customer's login has expired.");
    }
}
