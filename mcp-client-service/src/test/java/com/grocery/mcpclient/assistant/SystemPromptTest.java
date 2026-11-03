package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.context.Audience;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The real rules files (src/main/resources/prompts): who gets which rules, and what an answer must never repeat. */
class SystemPromptTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final SystemPrompt prompt = new SystemPrompt(read("prompts/system-prompt.txt"), read("prompts/panel-system-prompt.txt"));

    @Test
    void guestsAndCustomersGetTheShopRules() {
        String guest = prompt.render(TODAY, Audience.GUEST);
        assertThat(guest).startsWith("You are the shopping assistant of Fresh Grocery")
                .endsWith("Today is Tuesday, 27 October 2026. " + SystemPrompt.GUEST);
        assertThat(prompt.render(TODAY, Audience.CUSTOMER)).endsWith(SystemPrompt.LOGGED_IN);
    }

    @Test
    void everyPanelGetsThePanelRulesWithItsOwnPart() {
        assertThat(prompt.render(TODAY, Audience.VENDOR)).startsWith("You are the work assistant of Fresh Grocery")
                .contains(SystemPrompt.PANEL_USERS.get(Audience.VENDOR))
                .doesNotContain("{user}", "{today}", "getMyCart")
                .endsWith("Today is Tuesday, 27 October 2026.");
        assertThat(prompt.render(TODAY, Audience.DELIVERY_PARTNER)).contains("getPartnerDeliveries").doesNotContain("getVendor");
        assertThat(prompt.render(TODAY, Audience.ADMIN)).contains("getAdminWaitingWork").doesNotContain("getPartner");
    }

    @Test
    void everyLoggedInUserIsToldWhichToolsReadTheirNotifications_aGuestToLogIn() {
        for (Audience loggedIn : List.of(Audience.CUSTOMER, Audience.VENDOR, Audience.DELIVERY_PARTNER, Audience.ADMIN)) {
            assertThat(prompt.render(TODAY, loggedIn)).contains("getMyUnreadNotificationCount").contains("getMyNotifications");
        }
        assertThat(prompt.render(TODAY, Audience.GUEST)).doesNotContain("getMyNotifications")
                .contains("if they ask about their own cart, orders or notifications, or to change their cart, ask them to log in");
    }

    @Test
    void onlyALoggedInCustomerIsToldAboutTheCartTools() {
        assertThat(prompt.render(TODAY, Audience.CUSTOMER))
                .contains("prepareMyCartAdd", "prepareMyCartQuantity", "prepareMyCartRemoval", "prepareMyCartClear",
                        "prepareMyOrder", "prepareMyOrderCancellation");
        // a guest's rules name the prepare tools too (rule 9 of the shop), but only a customer is told to read their cart
        assertThat(prompt.render(TODAY, Audience.GUEST)).doesNotContain("call getMyCart");
        for (Audience panel : List.of(Audience.VENDOR, Audience.DELIVERY_PARTNER, Audience.ADMIN)) {
            assertThat(prompt.render(TODAY, panel)).doesNotContain("prepareMyCartAdd");
        }
    }

    @Test
    void theSentencesOfBothRulesTextsMayNotBeRepeated() {
        assertThat(prompt.ruleSentences())
                .contains("never use your own knowledge for these facts.")
                .contains("ask the user to log in to their panel again.")
                .doesNotHaveDuplicates();
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
