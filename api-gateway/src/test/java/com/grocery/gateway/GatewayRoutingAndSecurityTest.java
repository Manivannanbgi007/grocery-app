package com.grocery.gateway;

import com.grocery.gateway.support.FakeDownstreamService;
import com.grocery.gateway.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real gateway (random port) in front of a fake auth-service and checks
 * routing, JWT validation, role rules, user headers, correlation ids, CORS and error responses.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"RATE_LIMIT_REQUESTS_PER_SECOND=1000", "RATE_LIMIT_BURST=1000"})   // no rate limiting here
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)   // lets a test read the log output
class GatewayRoutingAndSecurityTest {

    private static final FakeDownstreamService AUTH_SERVICE = FakeDownstreamService.start("auth-service");
    private static final FakeDownstreamService STORE_SERVICE = FakeDownstreamService.start("store-service");
    private static final FakeDownstreamService PRODUCT_SERVICE = FakeDownstreamService.start("product-service");
    private static final FakeDownstreamService PRODUCT_READ_SERVICE = FakeDownstreamService.start("product-read-service");
    private static final FakeDownstreamService PLATFORM_SERVICE = FakeDownstreamService.start("platform-service");
    private static final FakeDownstreamService REPORT_SERVICE = FakeDownstreamService.start("report-service");
    private static final FakeDownstreamService DELIVERY_SERVICE = FakeDownstreamService.start("delivery-service");
    private static final FakeDownstreamService NOTIFICATION_SERVICE = FakeDownstreamService.start("notification-service");
    private static final FakeDownstreamService PAYMENT_SERVICE = FakeDownstreamService.start("payment-service");
    private static final FakeDownstreamService ORDER_SERVICE = FakeDownstreamService.start("order-service");
    private static final FakeDownstreamService CART_SERVICE = FakeDownstreamService.start("cart-service");
    private static final FakeDownstreamService INVENTORY_SERVICE = FakeDownstreamService.start("inventory-service");
    private static final FakeDownstreamService MCP_CLIENT_SERVICE = FakeDownstreamService.start("mcp-client-service");

    @DynamicPropertySource
    static void routeToFakeServices(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", AUTH_SERVICE::baseUrl);
        registry.add("STORE_SERVICE_URL", STORE_SERVICE::baseUrl);
        registry.add("PRODUCT_SERVICE_URL", PRODUCT_SERVICE::baseUrl);
        registry.add("PRODUCT_READ_SERVICE_URL", PRODUCT_READ_SERVICE::baseUrl);
        registry.add("PLATFORM_SERVICE_URL", PLATFORM_SERVICE::baseUrl);
        registry.add("REPORT_SERVICE_URL", REPORT_SERVICE::baseUrl);
        registry.add("DELIVERY_SERVICE_URL", DELIVERY_SERVICE::baseUrl);
        registry.add("NOTIFICATION_SERVICE_URL", NOTIFICATION_SERVICE::baseUrl);
        registry.add("PAYMENT_SERVICE_URL", PAYMENT_SERVICE::baseUrl);
        registry.add("ORDER_SERVICE_URL", ORDER_SERVICE::baseUrl);
        registry.add("CART_SERVICE_URL", CART_SERVICE::baseUrl);
        registry.add("INVENTORY_SERVICE_URL", INVENTORY_SERVICE::baseUrl);
        registry.add("MCP_CLIENT_SERVICE_URL", MCP_CLIENT_SERVICE::baseUrl);
    }

    @AfterAll
    static void stopFakeServices() {
        AUTH_SERVICE.close();
        STORE_SERVICE.close();
        PRODUCT_SERVICE.close();
        PRODUCT_READ_SERVICE.close();
        PLATFORM_SERVICE.close();
        REPORT_SERVICE.close();
        DELIVERY_SERVICE.close();
        NOTIFICATION_SERVICE.close();
        PAYMENT_SERVICE.close();
        ORDER_SERVICE.close();
        CART_SERVICE.close();
        INVENTORY_SERVICE.close();
        MCP_CLIENT_SERVICE.close();
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // ===== Public endpoints =====

    @Test
    void publicEndpoint_isRoutedWithoutToken_andClientUserHeadersAreRemoved() {
        client.post().uri("/api/v1/auth/login")
                .header("X-User-Id", "1")                    // a client trying to pretend to be user 1
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.path").isEqualTo("/api/v1/auth/login")
                .jsonPath("$.data.userId").isEmpty();
    }

    @Test
    void browsingStores_isPublic_butRegisteringAStoreNeedsTheVendorRole() {
        client.get().uri("/api/v1/stores?city=Bengaluru")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.path").isEqualTo("/api/v1/stores");

        client.post().uri("/api/v1/vendor/store")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void catalog_readsGoToTheReadService_andWritesToTheWriteService() {
        // GET -> product-read-service (Elasticsearch), public
        client.get().uri("/api/v1/products?q=tomato")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("product-read-service");

        // Same path family, vendor listing -> read service; creating a product -> write service
        getWithToken("/api/v1/vendor/products", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("product-read-service");

        client.post().uri("/api/v1/vendor/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.vendor())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("product-service")
                .jsonPath("$.data.storeId").isEqualTo("1");

        // Product images are public and served by product-service
        client.get().uri("/media/products/1/photo.jpg")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("product-service");
    }

    @Test
    void inventory_isManagedByVendors_andViewedByAdmins() {
        client.put().uri("/api/v1/vendor/inventory/7")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.vendor())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("inventory-service")
                .jsonPath("$.data.storeId").isEqualTo("1");

        getWithToken("/api/v1/admin/inventory?lowStockOnly=true", TestTokens.admin())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("inventory-service");

        getWithToken("/api/v1/vendor/inventory", TestTokens.customer())
                .expectStatus().isForbidden();
    }

    @Test
    void cart_isForCustomers_whileOffersArePublic() {
        getWithToken("/api/v1/cart", TestTokens.customer())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("cart-service")
                .jsonPath("$.data.userId").isEqualTo("4");

        client.get().uri("/api/v1/coupons?storeId=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("cart-service");

        getWithToken("/api/v1/cart", TestTokens.vendor()).expectStatus().isForbidden();
        getWithToken("/api/v1/vendor/coupons", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("cart-service");
    }

    @Test
    void orders_andPayments_areRoutedByPanel() {
        client.post().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("order-service");

        getWithToken("/api/v1/vendor/orders?status=CONFIRMED", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("order-service");

        // The vendor panel's live stream has a route of its own (no response timeout), to the same service, vendors only
        getWithToken("/api/v1/vendor/orders/stream", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("order-service");
        getWithToken("/api/v1/vendor/orders/stream", TestTokens.customer()).expectStatus().isForbidden();

        getWithToken("/api/v1/payments/orders/1", TestTokens.customer())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("payment-service");

        getWithToken("/api/v1/admin/payments", TestTokens.admin())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("payment-service");

        getWithToken("/api/v1/admin/orders", TestTokens.customer()).expectStatus().isForbidden();
    }

    @Test
    void paymentWebhook_needsNoToken_butOtherPaymentCallsDo() {
        client.post().uri("/api/v1/payments/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"event\":\"payment.captured\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("payment-service")
                .jsonPath("$.data.userId").doesNotExist();

        client.get().uri("/api/v1/payments/webhooks/razorpay").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/payments/orders/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void notifications_areForEveryLoggedInUser_andAdminsSeeAll() {
        getWithToken("/api/v1/notifications", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("notification-service")
                .jsonPath("$.data.userId").isEqualTo("3");
        getWithToken("/api/v1/notifications/unread-count", TestTokens.customer()).expectStatus().isOk();
        client.get().uri("/api/v1/notifications").exchange().expectStatus().isUnauthorized();

        getWithToken("/api/v1/admin/notifications", TestTokens.admin()).expectStatus().isOk();
        getWithToken("/api/v1/admin/notifications", TestTokens.vendor()).expectStatus().isForbidden();
    }

    @Test
    void deliveries_areForDeliveryPartners_andAdminsManagePartnersAndZones() {
        getWithToken("/api/v1/delivery/deliveries", TestTokens.deliveryPartner())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("delivery-service")
                .jsonPath("$.data.userId").isEqualTo("5");
        getWithToken("/api/v1/delivery/deliveries", TestTokens.customer()).expectStatus().isForbidden();

        getWithToken("/api/v1/admin/delivery-partners?kycStatus=PENDING", TestTokens.admin())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("delivery-service");
        getWithToken("/api/v1/admin/delivery-zones", TestTokens.admin()).expectStatus().isOk();
        getWithToken("/api/v1/admin/deliveries", TestTokens.deliveryPartner()).expectStatus().isForbidden();

        getWithToken("/delivery-media/proofs/1.jpg", TestTokens.customer())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("delivery-service");
        client.get().uri("/delivery-media/proofs/1.jpg").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void liveDeliveryTracking_customersFollow_partnersShareTheirLocation() {
        getWithToken("/api/v1/delivery-tracking/orders/18", TestTokens.customer())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("delivery-service")
                .jsonPath("$.data.userId").isEqualTo("4");
        getWithToken("/api/v1/delivery-tracking/orders/18/stream", TestTokens.customer())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.path").isEqualTo("/api/v1/delivery-tracking/orders/18/stream");
        getWithToken("/api/v1/delivery-tracking/orders/18", TestTokens.deliveryPartner()).expectStatus().isForbidden();
        client.get().uri("/api/v1/delivery-tracking/orders/18/stream").exchange().expectStatus().isUnauthorized();

        client.put().uri("/api/v1/delivery/deliveries/7/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.deliveryPartner())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("delivery-service");
        client.put().uri("/api/v1/delivery/deliveries/7/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void reviews_areReadByEverybody_writtenByCustomers_andRoutedToTheWriteService() {
        client.get().uri("/api/v1/products/1/reviews").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("product-service");
        client.get().uri("/api/v1/products/1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("product-read-service");

        client.post().uri("/api/v1/products/1/reviews")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("product-service");
        client.post().uri("/api/v1/products/1/reviews").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();

        getWithToken("/api/v1/vendor/reviews", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("product-service");
        getWithToken("/api/v1/admin/reviews", TestTokens.vendor()).expectStatus().isForbidden();

        client.post().uri("/api/v1/delivery-ratings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("delivery-service");
    }

    @Test
    void reportsAndPlatformSettings_areRoutedByPanel() {
        getWithToken("/api/v1/admin/reports/dashboard", TestTokens.admin())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("report-service");
        getWithToken("/api/v1/vendor/reports/sales/daily", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("report-service");
        getWithToken("/api/v1/super-admin/reports/commissions", TestTokens.admin()).expectStatus().isForbidden();
        getWithToken("/api/v1/super-admin/reports/commissions", TestTokens.superAdmin()).expectStatus().isOk();

        client.get().uri("/api/v1/platform/settings").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("platform-service");
        client.get().uri("/api/v1/banners").exchange().expectStatus().isOk();
        client.put().uri("/api/v1/super-admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isForbidden();
        getWithToken("/api/v1/super-admin/audit-log", TestTokens.superAdmin())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("platform-service");
        getWithToken("/api/v1/super-admin/dead-letters", TestTokens.admin()).expectStatus().isForbidden();
        getWithToken("/api/v1/super-admin/dead-letters/grocery.order.events.DLT", TestTokens.superAdmin())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("platform-service");
        getWithToken("/api/v1/admin/banners", TestTokens.admin())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("platform-service");
    }

    @Test
    void shoppingAssistant_isOpenForGuests_andKnowsLoggedInUsers() {
        client.post().uri("/api/v1/chat/messages")
                .header("X-User-Id", "1")                    // a guest trying to pretend to be user 1
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\": \"Do you have milk?\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("mcp-client-service")
                .jsonPath("$.data.userId").isEmpty();

        client.post().uri("/api/v1/chat/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\": \"Do you have milk?\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("4");

        client.delete().uri("/api/v1/chat/conversations/3f0c8a8e-2d6b-4c1e-9a43-8f1f4f3c2b10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("mcp-client-service")
                .jsonPath("$.data.method").isEqualTo("DELETE");
    }

    @Test
    void voice_isOpenForGuests_andKnowsLoggedInUsers() {
        MultipartBodyBuilder recording = new MultipartBodyBuilder();
        recording.part("audio", new byte[] {1, 2, 3}).filename("question.webm").contentType(MediaType.parseMediaType("audio/webm"));
        client.post().uri("/api/v1/chat/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(recording.build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("mcp-client-service")
                .jsonPath("$.data.path").isEqualTo("/api/v1/chat/transcriptions")
                .jsonPath("$.data.userId").isEmpty();

        client.post().uri("/api/v1/chat/speech")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\": \"Amul Butter costs 58 rupees.\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.path").isEqualTo("/api/v1/chat/speech")
                .jsonPath("$.data.userId").isEqualTo("4");
    }

    @Test
    void theAssistantsToolsAreNotReachableThroughTheGateway() {
        client.post().uri("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void customers_cannotChangeProducts() {
        client.post().uri("/api/v1/vendor/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ===== Missing or bad tokens -> 401 =====

    @Test
    void protectedEndpoint_withoutToken_returns401InStandardShape() {
        client.get().uri("/api/v1/auth/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Please log in to access this resource")
                .jsonPath("$.path").isEqualTo("/api/v1/auth/me");
    }

    @Test
    void expiredToken_returns401() {
        getWithToken("/api/v1/auth/me", TestTokens.expiredCustomer())
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Your login has expired. Please refresh the access token or log in again.");
    }

    @Test
    void expiredAndInvalidTokens_areToldApart_inTheStandardHeaderToo() {
        getWithToken("/api/v1/auth/me", TestTokens.expiredCustomer())
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token expired\"");
        getWithToken("/api/v1/auth/me", TestTokens.signedWithWrongSecret())
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token is invalid\"")
                .expectBody().jsonPath("$.message").isEqualTo("Your login token is invalid. Please log in again.");
    }

    @Test
    void refresh_withAnExpiredAccessTokenStillAttached_reachesAuthService_andTheCookiesPassThrough() {
        client.post().uri("/api/v1/auth/refresh")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expiredCustomer())   // ignored here
                .cookie("refresh_token", "old-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Set-Cookie", "refresh_token=new-token; Path=/api/v1/auth; HttpOnly; SameSite=Strict")
                .expectBody()
                .jsonPath("$.data.service").isEqualTo("auth-service")
                .jsonPath("$.data.cookie").isEqualTo("refresh_token=old-token")
                .jsonPath("$.data.userId").doesNotExist();   // public endpoint: no user headers from the ignored token
    }

    @Test
    void logout_isOpenToEveryRole_andToGuests() {
        client.post().uri("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.vendor())
                .exchange().expectStatus().isOk();
        client.post().uri("/api/v1/auth/logout").exchange().expectStatus().isOk();
    }

    @Test
    void socialLogin_theRedirectAndTheProvidersAnswer_areOpen_evenAppleFormPostFromItsOwnSite() {
        client.get().uri("/api/v1/auth/oauth2/authorization/google").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.data.service").isEqualTo("auth-service");
        client.get().uri("/api/v1/auth/social/providers").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/auth/login/oauth2/code/google?code=c&state=s").exchange().expectStatus().isOk();

        // Apple posts its answer as a form from https://appleid.apple.com: the CORS check must not refuse it (403)
        client.post().uri("/api/v1/auth/login/oauth2/code/apple")
                .header(HttpHeaders.ORIGIN, "https://appleid.apple.com")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).bodyValue("code=c&state=s")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.service").isEqualTo("auth-service");
        // Every other call from a foreign site is still refused
        client.get().uri("/api/v1/products").header(HttpHeaders.ORIGIN, "https://appleid.apple.com")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void socialLoginSteps_ignoreAnOldAccessToken_likeLogin_andGetTheirCookie() {
        for (String step : List.of("session", "signup", "link")) {
            client.post().uri("/api/v1/auth/social/" + step)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expiredCustomer())   // ignored here
                    .cookie("social_flow", "this-browser")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.cookie").isEqualTo("social_flow=this-browser")
                    .jsonPath("$.data.userId").doesNotExist();
        }
    }

    @Test
    void tokenSignedWithAnotherSecret_returns401() {
        getWithToken("/api/v1/auth/me", TestTokens.signedWithWrongSecret()).expectStatus().isUnauthorized();
    }

    @Test
    void tokenFromAnotherIssuer_returns401() {
        getWithToken("/api/v1/auth/me", TestTokens.fromAnotherIssuer()).expectStatus().isUnauthorized();
    }

    // ===== User headers =====

    @Test
    void validToken_isForwardedAsUserHeaders() {
        getWithToken("/api/v1/auth/me", TestTokens.customer())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("4")
                .jsonPath("$.data.userRole").isEqualTo("CUSTOMER")
                .jsonPath("$.data.storeId").isEmpty();
    }

    @Test
    void vendorToken_alsoForwardsStoreId() {
        getWithToken("/api/v1/auth/me", TestTokens.vendor())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userRole").isEqualTo("VENDOR")
                .jsonPath("$.data.storeId").isEqualTo("1");
    }

    @Test
    void spoofedUserHeaders_areReplacedWithValuesFromTheToken() {
        client.get().uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .header("X-User-Id", "1")
                .header("X-User-Role", "SUPER_ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("4")
                .jsonPath("$.data.userRole").isEqualTo("CUSTOMER");
    }

    // ===== Role rules -> 403 or routed =====

    @Test
    void customer_cannotCallAdminEndpoints() {
        getWithToken("/api/v1/admin/users", TestTokens.customer())
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("You do not have permission to access this resource");
    }

    @Test
    void admin_canCallAdminEndpoints() {
        getWithToken("/api/v1/admin/users", TestTokens.admin()).expectStatus().isOk();
    }

    @Test
    void admin_cannotCallSuperAdminEndpoints() {
        client.post().uri("/api/v1/super-admin/admins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void superAdmin_canCallSuperAdminAndAdminEndpoints() {
        client.post().uri("/api/v1/super-admin/admins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.superAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        getWithToken("/api/v1/admin/users", TestTokens.superAdmin()).expectStatus().isOk();
    }

    @Test
    void vendor_cannotCallCustomerEndpoints() {
        getWithToken("/api/v1/orders", TestTokens.vendor()).expectStatus().isForbidden();
    }

    @Test
    void internalEndpoints_areNeverReachableFromOutside() {
        getWithToken("/internal/users/1", TestTokens.superAdmin()).expectStatus().isForbidden();
    }

    // ===== Errors, correlation id, CORS, Swagger =====

    @Test
    void pathWithoutRoute_returns404InStandardShape() {
        getWithToken("/api/v1/no-such-endpoint", TestTokens.customer())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("No route for GET /api/v1/no-such-endpoint");
    }

    @Test
    void correlationId_isCreated_andSentToTheServiceAndBackToTheClient() {
        WebTestClient.ResponseSpec response = getWithToken("/api/v1/auth/me", TestTokens.customer());

        String correlationId = response.returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-Id");
        response.expectBody().jsonPath("$.data.correlationId").isEqualTo(correlationId);
    }

    @Test
    void correlationId_fromTheClient_isKept() {
        client.get().uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .header("X-Correlation-Id", "my-request-123")
                .exchange()
                .expectHeader().valueEquals("X-Correlation-Id", "my-request-123")
                .expectBody()
                .jsonPath("$.data.correlationId").isEqualTo("my-request-123");
    }

    @Test
    void theRequestLogLine_hasTheCorrelationIdInItsMdc(CapturedOutput output) throws InterruptedException {
        client.get().uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.customer())
                .header("X-Correlation-Id", "mdc-check-42")
                .exchange()
                .expectStatus().isOk();

        // logging.pattern.correlation prints "[<correlation id>] [<trace id>] " in front of the logger name.
        // The line is written when the request has finished, which can be a moment after the client got the answer.
        Pattern logLine = Pattern.compile("\\[mdc-check-42] \\[[0-9a-f]*] \\S*RequestLoggingFilter\\s*: GET /api/v1/auth/me -> 200");
        for (int i = 0; i < 50 && !logLine.matcher(output.getOut()).find(); i++) {
            Thread.sleep(100);
        }
        assertThat(output.getOut()).containsPattern(logLine);
    }

    @Test
    void corsPreflight_allowsTheFrontendOrigin() {
        client.options().uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void corsPreflight_rejectsUnknownOrigins() {
        client.options().uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void serviceApiDocs_areRoutedToTheService() {
        client.get().uri("/auth-service/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.path").isEqualTo("/v3/api-docs");
    }

    @Test
    void healthCheck_isPublic() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    private WebTestClient.ResponseSpec getWithToken(String path, String token) {
        return client.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }
}
