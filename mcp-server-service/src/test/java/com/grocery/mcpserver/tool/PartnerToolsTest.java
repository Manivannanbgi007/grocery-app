package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.PartnerClient;
import com.grocery.mcpserver.client.dto.AvailabilityData;
import com.grocery.mcpserver.client.dto.DeliveryData;
import com.grocery.mcpserver.client.dto.EarningsData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.PartnerData;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerToolsTest {

    private static final McpTransportContext LOGGED_IN = McpTransportContext.create(Map.of(McpTransportConfig.USER_TOKEN, "partner-jwt"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T04:30:00Z"), ZoneId.of("Asia/Kolkata"));

    @Mock
    private PartnerClient partners;

    private PartnerTools tools;

    @BeforeEach
    void setUp() {
        tools = new PartnerTools(partners, new ToolRunner(), CLOCK);
    }

    @Test
    void withoutALoginTheToolsAskThePartnerToLogIn() {
        assertThat(tools.getPartnerStatus(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getPartnerDeliveries(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getPartnerEarnings(null, McpTransportContext.EMPTY).message()).isEqualTo(PartnerTools.NOT_LOGGED_IN);
        verifyNoInteractions(partners);
    }

    @Test
    void anOnlinePartnerSeesUntilWhenAndTheirKycAndRating() {
        when(partners.profile("partner-jwt")).thenReturn(Optional.of(new PartnerData("Arjun Singh", "SCOOTER", "Indiranagar",
                "APPROVED", null, true, new BigDecimal("4.50"), 12)));
        when(partners.availability("partner-jwt")).thenReturn(new AvailabilityData(true, Instant.parse("2026-10-27T05:00:00Z")));

        var result = tools.getPartnerStatus(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The partner is online and gets new deliveries.");
        assertThat(result.data().name()).isEqualTo("Arjun Singh");
        assertThat(result.data().onlineUntil()).isEqualTo("27 Oct 2026, 10:30 am");
        assertThat(result.data().kyc()).isEqualTo("Approved");
        assertThat(result.data().zone()).isEqualTo("Indiranagar");
        assertThat(result.data().vehicle()).isEqualTo("Scooter");
        assertThat(result.data().rating()).isEqualTo("4.5 stars from 12 ratings");
    }

    @Test
    void anOfflinePartnerWithARejectedKycIsToldWhy() {
        when(partners.profile("partner-jwt")).thenReturn(Optional.of(new PartnerData("Arjun Singh", "SCOOTER", "Indiranagar",
                "REJECTED", "The licence photo is not readable", false, null, 0)));
        when(partners.availability("partner-jwt")).thenReturn(new AvailabilityData(false, null));

        var result = tools.getPartnerStatus(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The partner is offline: no new deliveries come to them until they go online "
                + "in the delivery panel. Deliveries start only once the KYC is approved.");
        assertThat(result.data().kyc()).isEqualTo("Rejected");
        assertThat(result.data().kycNote()).isEqualTo("The licence photo is not readable");
        assertThat(result.data().onlineUntil()).isNull();
        assertThat(result.data().rating()).isNull();
    }

    @Test
    void aProfileThatIsStillBeingCreatedIsNotFound() {
        when(partners.profile("partner-jwt")).thenReturn(Optional.empty());

        var result = tools.getPartnerStatus(LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.message()).isEqualTo(PartnerTools.NO_PROFILE);
    }

    @Test
    void currentDeliveriesShowTheCashToCollectOnlyForCashOrders() {
        when(partners.activeDeliveries("partner-jwt", PartnerTools.CURRENT_DELIVERIES)).thenReturn(new PageData<>(List.of(
                delivery(901, "PICKED_UP", "COD", "177.00"),
                delivery(902, "ASSIGNED", "ONLINE", "0.00")), 2));

        var result = tools.getPartnerDeliveries(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The partner has 2 current deliveries.");
        assertThat(result.data().get(0)).satisfies(delivery -> {
            assertThat(delivery.orderNumber()).isEqualTo(901);
            assertThat(delivery.step()).isEqualTo("Picked up at the store");
            assertThat(delivery.store()).isEqualTo("Fresh Mart");
            assertThat(delivery.customer()).isEqualTo("Priya Sharma");
            assertThat(delivery.cashToCollect()).isEqualTo("₹177");
            assertThat(delivery.payout()).isEqualTo("₹40");
        });
        assertThat(result.data().get(1)).satisfies(delivery -> {
            assertThat(delivery.step()).isEqualTo("Given to the partner, not accepted yet");
            assertThat(delivery.cashToCollect()).isEqualTo("Nothing (paid online)");
        });
    }

    @Test
    void noCurrentDeliveryIsSaidPlainly() {
        when(partners.activeDeliveries("partner-jwt", PartnerTools.CURRENT_DELIVERIES)).thenReturn(new PageData<>(List.of(), 0));

        var result = tools.getPartnerDeliveries(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The partner has no current deliveries.");
        assertThat(result.data()).isNull();
    }

    @Test
    void earningsCoverTheAskedDays() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        when(partners.earnings("partner-jwt", LocalDate.of(2026, 9, 28), today)).thenReturn(new EarningsData(
                LocalDate.of(2026, 9, 28), today, 34, new BigDecimal("1360.00"),
                List.of(new EarningsData.Day(LocalDate.of(2026, 10, 26), 13, new BigDecimal("520.00")),
                        new EarningsData.Day(today, 21, new BigDecimal("840.00")))));

        var result = tools.getPartnerEarnings(30, LOGGED_IN);

        assertThat(result.message()).isEqualTo("The partner's earnings of the last 30 days, 28 Sep 2026 to 27 Oct 2026.");
        assertThat(result.data().deliveries()).isEqualTo(34);
        assertThat(result.data().earned()).isEqualTo("₹1,360");
        assertThat(result.data().perDay()).extracting(day -> day.date() + ": " + day.deliveries() + " for " + day.earned())
                .containsExactly("26 Oct 2026: 13 for ₹520", "27 Oct 2026: 21 for ₹840");
        assertThat(tools.getPartnerEarnings(120, LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
    }

    private static DeliveryData delivery(long orderId, String status, String paymentMethod, String toCollect) {
        return new DeliveryData(orderId + 1000, orderId, status, "Fresh Mart", "45, 100 Feet Road, Indiranagar",
                "Priya Sharma", "Flat 302, Lakeview Apartments", paymentMethod, new BigDecimal(toCollect),
                new BigDecimal("40.00"), Instant.parse("2026-10-27T03:00:00Z"));
    }
}
