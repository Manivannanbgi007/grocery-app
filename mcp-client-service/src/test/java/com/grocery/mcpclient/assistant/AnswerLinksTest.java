package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.dto.response.ChatLink;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The links of an answer come from the tool results, and only for what the answer talks about. */
class AnswerLinksTest {

    private static final String PRODUCTS = "{\"status\":\"OK\",\"message\":\"Found 2 products.\",\"data\":["
            + "{\"productId\":12,\"name\":\"Amul Butter\",\"price\":\"₹56\"},"
            + "{\"productId\":13,\"name\":\"Amul Cheese Slices\",\"price\":\"₹135\"}]}";

    @Test
    void aProductGetsALinkWhenTheAnswerNamesIt() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("searchProducts", "{\"query\":\"amul\"}", PRODUCTS);

        assertThat(AnswerLinks.of("Yes, amul butter costs ₹56.", recorder, Audience.CUSTOMER))
                .containsExactly(new ChatLink("Amul Butter", "/products/12"));
    }

    @Test
    void theProductOfReviewsAndAnOrderTheAnswerNamesGetLinks() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("getProductReviews", "{}",
                "{\"status\":\"OK\",\"data\":{\"productId\":7,\"productName\":\"Tomato\",\"rating\":\"4.5\"}}");
        recorder.recordResult("getMyOrders", "{}",
                "{\"status\":\"OK\",\"data\":[{\"orderNumber\":902,\"status\":\"Delivered\"},{\"orderNumber\":901}]}");

        assertThat(AnswerLinks.of("Tomato has 4.5 stars. Your last order 902 was delivered.", recorder, Audience.CUSTOMER)).containsExactly(
                new ChatLink("Tomato", "/products/7"),
                new ChatLink("Order #902", "/orders/902"),
                new ChatLink("Your orders", "/orders"));
    }

    @Test
    void theNotificationToolsLinkToTheNotificationsPageOfTheUsersOwnPanel() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("getMyUnreadNotificationCount", "{}",
                "{\"status\":\"OK\",\"message\":\"The user has 3 unread notifications.\",\"data\":3}");

        assertThat(AnswerLinks.of("You have 3 unread notifications.", recorder, Audience.CUSTOMER))
                .containsExactly(new ChatLink("Your notifications", "/account/notifications"));
        assertThat(AnswerLinks.of("You have 3 unread notifications.", recorder, Audience.VENDOR))
                .containsExactly(new ChatLink("Notifications", "/vendor/notifications"));
        assertThat(AnswerLinks.of("You have 3 unread notifications.", recorder, Audience.DELIVERY_PARTNER))
                .containsExactly(new ChatLink("Notifications", "/delivery/notifications"));
        assertThat(AnswerLinks.of("You have 3 unread notifications.", recorder, Audience.ADMIN))
                .containsExactly(new ChatLink("Notifications", "/admin/notifications"));
    }

    @Test
    void aToolsTopicGetsItsPageOnceAndThePanelsTheirOwnPages() {
        var recorder = new ToolCallRecorder();
        recorder.recordResult("getWishlist", "{}", "{\"status\":\"OK\",\"data\":{\"count\":1,\"products\":[\"Tomato\"]}}");
        recorder.recordResult("getWishlist", "{}", "{\"status\":\"OK\",\"data\":{\"count\":1,\"products\":[\"Tomato\"]}}");
        recorder.recordResult("getVendorLowStock", "{}", "{\"status\":\"OK\",\"data\":[]}");

        assertThat(AnswerLinks.of("1 product is on your wishlist.", recorder, Audience.CUSTOMER)).containsExactly(
                new ChatLink("Your wishlist", "/wishlist"),
                new ChatLink("Products", "/vendor/products"));
    }

    @Test
    void noLinksFromFailedOrUnsuccessfulToolsOrBrokenResults() {
        var recorder = new ToolCallRecorder();
        recorder.recordFailure("getMyCart", "{}");
        recorder.recordResult("listStores", "{}", "{\"status\":\"SERVICE_UNAVAILABLE\",\"message\":\"Not now.\"}");
        recorder.recordResult("getWishlist", "{}", "not json");
        recorder.recordResult("getDeliveryRules", "{}", "{\"status\":\"OK\",\"data\":{\"fee\":\"₹30\"}}");

        assertThat(AnswerLinks.of("Delivery costs ₹30.", recorder, Audience.CUSTOMER)).isEmpty();
    }

    @Test
    void atMostFourLinks() {
        var recorder = new ToolCallRecorder();
        for (String tool : new String[] {"getWishlist", "getMyCart", "getMyOrders", "listStores", "listCurrentOffers"}) {
            recorder.recordResult(tool, "{}", "{\"status\":\"OK\",\"data\":null}");
        }

        assertThat(AnswerLinks.of("Here you go.", recorder, Audience.CUSTOMER)).hasSize(AnswerLinks.MAX_LINKS)
                .extracting(ChatLink::path).containsExactly("/wishlist", "/cart", "/orders", "/stores");
    }

    @Test
    void theCartToolsLinkTheCartWithACardAndAfterANo() {
        var prepared = new ToolCallRecorder("add 2 kg of tomatoes");
        prepared.recordResult("prepareMyCartAdd", "{}", "{\"status\":\"OK\",\"message\":\"Adding prepared; NOTHING is added yet.\"}");
        var refused = new ToolCallRecorder("add 2 kg of tomatoes");
        refused.recordResult("prepareMyCartAdd", "{}", "{\"status\":\"REFUSED\",\"message\":\"The cart has products from Green Basket, "
                + "and Amul Butter is sold by Fresh Mart.\"}");
        var asked = new ToolCallRecorder("add 2 kg of tomatoes");
        asked.recordResult("prepareMyCartAdd", "{}", "{\"status\":\"CHOOSE\",\"message\":\"Which one?\",\"data\":{\"options\":[]}}");

        assertThat(AnswerLinks.of("Here is the product to add to your cart.", prepared, Audience.CUSTOMER))
                .containsExactly(new ChatLink("Your cart", "/cart"));
        assertThat(AnswerLinks.of("Your cart has products from Green Basket.", refused, Audience.CUSTOMER))
                .containsExactly(new ChatLink("Your cart", "/cart"));
        assertThat(AnswerLinks.of("Which milk do you mean?", asked, Audience.CUSTOMER)).isEmpty();
    }

    @Test
    void aCartThatCannotBeOrderedLinksTheCart_andAMissingAddressTheAddresses() {
        var notReady = new ToolCallRecorder();
        notReady.recordResult("prepareMyOrder", "{}", "{\"status\":\"REFUSED\",\"message\":\"The cart cannot be ordered yet: x.\"}");
        var noAddress = new ToolCallRecorder();
        noAddress.recordResult("prepareMyOrder", "{}", "{\"status\":\"NOT_FOUND\",\"message\":\"The customer has no delivery address yet.\"}");

        assertThat(AnswerLinks.of("Your cart cannot be ordered yet.", notReady, Audience.CUSTOMER))
                .containsExactly(new ChatLink("Your cart", "/cart"));
        assertThat(AnswerLinks.of("Please add a delivery address first.", noAddress, Audience.CUSTOMER))
                .containsExactly(new ChatLink("Your addresses", "/account/addresses"));
    }
}
