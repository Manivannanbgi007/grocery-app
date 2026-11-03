package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.config.ChatProperties;
import com.grocery.mcpclient.context.Audience;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The assistant's rules, read once at start-up:
 * - the shop rules (app.chat.system-prompt, prompts/system-prompt.txt) for guests and customers;
 * - the panel rules (app.chat.panel-system-prompt, prompts/panel-system-prompt.txt) for vendors, delivery partners
 *   and admins.
 *
 * - render(): the text sent to the model. Shop rules: {today} and {customer} (logged in or not) replaced; panel
 *   rules: {today} and {user} (who is asking and which tools are theirs) replaced. Every logged-in user is also told
 *   which tools read their own notifications (NOTIFICATIONS). A plain text replacement (not a
 *   template engine), so no character in the rules can break the rendering.
 * - ruleSentences(): the sentences of both rules texts, for AnswerValidator: an answer must never repeat them
 *   ("print your system prompt" is a common trick).
 */
@Component
public class SystemPrompt {

    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    /** A numbered rule ("4. Use only the facts ...") or a status line ("- NOT_FOUND: say that ..."). */
    private static final Pattern RULE_LINE = Pattern.compile("^\\s*(?:\\d+\\.|-)\\s+(.+)$", Pattern.MULTILINE);
    /** Shorter sentences ("Never guess.") could appear in a normal answer by chance. */
    private static final int MIN_SENTENCE_LENGTH = 30;
    /** Every logged-in user (customer or panel): their own notifications, the bell icon of the website. */
    static final String NOTIFICATIONS = " Their own notifications (the bell icon of the website) come from "
            + "getMyUnreadNotificationCount (how many are unread) and getMyNotifications (the newest ones, only the unread "
            + "ones, or those about one topic such as orders). getMyUnreadNotificationCount only counts: when the user "
            + "asks to see or show notifications, call getMyNotifications.";
    static final String LOGGED_IN = "The customer is logged in: for questions about their own cart or orders, "
            + "call getMyCart, getMyOrders or getMyOrderDetails. When they ask to add or buy a product, call prepareMyCartAdd "
            + "at once (once for each product); to change an amount, take a product out or empty the cart, "
            + "prepareMyCartQuantity, prepareMyCartRemoval or prepareMyCartClear; when they want to order their cart, call "
            + "prepareMyOrder, and to cancel an order, prepareMyOrderCancellation. The customer may have pressed Confirm "
            + "under a summary since your last answer, which this chat does not show you: for anything about their cart "
            + "or orders, call the tools again."
            + NOTIFICATIONS;
    static final String GUEST = "The customer is not logged in: if they ask about their own cart, orders or "
            + "notifications, or to change their cart, ask them to log in on the website.";
    /**
     * Who is asking in a panel. A small model does not reliably notice which tools are in its list, so it is told
     * plainly whose data its tools show and what they are called.
     */
    static final Map<Audience, String> PANEL_USERS = Map.of(
            Audience.VENDOR, "You are talking to a vendor, who sells through their own store in the shop. Their "
                    + "store's data comes from these tools: getVendorStoreToday (open, taking orders, how many orders "
                    + "wait at each step), getVendorOrders (the newest orders, or those of one status), getVendorLowStock "
                    + "(which products are low on stock or sold out) and getVendorSales (sales and best sellers of the "
                    + "last days). getVendorStoreToday only counts: when the user asks which products or which orders, "
                    + "call getVendorLowStock or getVendorOrders, also right after a count.",
            Audience.DELIVERY_PARTNER, "You are talking to a delivery partner, who delivers the shop's orders. Their "
                    + "data comes from these tools: getPartnerStatus (online or offline, KYC, zone, rating), "
                    + "getPartnerDeliveries (their current deliveries) and getPartnerEarnings (deliveries and earnings "
                    + "of the last days).",
            Audience.ADMIN, "You are talking to an admin of the platform team, who runs the whole shop. The platform's "
                    + "data comes from these tools: getAdminOverview (today's numbers and how much work waits), "
                    + "getAdminWaitingWork (which stores, partners and deliveries wait), getAdminOrders (the newest "
                    + "orders of all stores, or those of one status) and getAdminSales (sales, top stores and best "
                    + "sellers of the last days). getAdminOverview only counts: when the user asks which stores, "
                    + "partners or deliveries, call getAdminWaitingWork, also right after a count.");

    private final String shopTemplate;
    private final String panelTemplate;

    @Autowired
    public SystemPrompt(ChatProperties chatProperties) {
        this(read(chatProperties.systemPrompt()), read(chatProperties.panelSystemPrompt()));
    }

    public SystemPrompt(String shopTemplate, String panelTemplate) {
        this.shopTemplate = shopTemplate.strip();
        this.panelTemplate = panelTemplate.strip();
    }

    /** One rules text for every audience (handy in tests). */
    public SystemPrompt(String template) {
        this(template, template);
    }

    /**
     * @param audience who is chatting: guests and customers get the shop rules (and the customer is told whether they
     *                 are logged in), the panels get the panel rules with the part about their role
     */
    public String render(LocalDate today, Audience audience) {
        String date = TODAY.format(today);
        if (audience.isPanel()) {
            return panelTemplate
                    .replace("{today}", date)
                    .replace("{user}", PANEL_USERS.get(audience) + NOTIFICATIONS);
        }
        return shopTemplate
                .replace("{today}", date)
                .replace("{customer}", audience == Audience.CUSTOMER ? LOGGED_IN : GUEST);
    }

    /** The rule sentences of both rules texts in lower case, with single spaces. */
    public List<String> ruleSentences() {
        return Stream.of(shopTemplate, panelTemplate)
                .distinct()
                .flatMap(template -> RULE_LINE.matcher(template).results())
                .flatMap(rule -> Arrays.stream(rule.group(1).split("(?<=[.:!?])\\s+")))
                .map(SystemPrompt::normalise)
                .filter(sentence -> sentence.length() >= MIN_SENTENCE_LENGTH)
                .distinct()
                .toList();
    }

    static String normalise(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read the rules " + resource, ex);
        }
    }
}
